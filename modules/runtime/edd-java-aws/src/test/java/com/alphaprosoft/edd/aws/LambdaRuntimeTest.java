package com.alphaprosoft.edd.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LambdaRuntimeTest {

  record IngestCommand(UUID id, String source) implements Command {}

  record NotifyCommand(UUID id) implements Command {}

  record GetSource(UUID id) implements Query {}

  record IngestedEvent(UUID id, String source) implements Event {}

  record NotifiedEvent(UUID id) implements Event {}

  record IngestAggregate(UUID id, long version) implements Aggregate {
    static IngestAggregate ingested(IngestAggregate a, IngestedEvent e) {
      return new IngestAggregate(e.id(), 0);
    }

    static IngestAggregate notified(IngestAggregate a, NotifiedEvent e) {
      return new IngestAggregate(e.id(), 0);
    }
  }

  public static final class IngestHandler
      implements CommandHandler<IngestCommand, IngestAggregate> {
    @Override
    public List<CommandEmission> handle(CommandContext<IngestAggregate> ctx, IngestCommand cmd) {
      return List.of(new IngestedEvent(cmd.id(), cmd.source()));
    }
  }

  public static final class NotifyHandler
      implements CommandHandler<NotifyCommand, IngestAggregate> {
    @Override
    public List<CommandEmission> handle(CommandContext<IngestAggregate> ctx, NotifyCommand cmd) {
      return List.of(new NotifiedEvent(cmd.id()));
    }
  }

  static final CommandId<IngestCommand> INGEST = CommandId.of("ingest", IngestCommand.class);
  static final CommandId<NotifyCommand> NOTIFY = CommandId.of("notify", NotifyCommand.class);
  static final EventId<IngestedEvent> INGESTED = EventId.of("ingested", IngestedEvent.class);
  static final EventId<NotifiedEvent> NOTIFIED = EventId.of("notified", NotifiedEvent.class);
  static final QueryId<GetSource, String> GET_SOURCE =
      QueryId.of("get-source", GetSource.class, String.class);

  private static final class CapturingRouter implements Router {
    final List<Command> commands = new ArrayList<>();
    final List<RequestMeta> metas = new ArrayList<>();

    @Override
    public void route(Command command, RequestMeta meta) {
      commands.add(command);
      metas.add(meta);
    }
  }

  private InMemoryEventStore events;
  private CapturingRouter router;

  private LambdaRuntime runtime() {
    events = InMemoryEventStore.builder().build();
    router = new CapturingRouter();
    Application app =
        Application.builder("ingest-svc")
            .eventStore(events)
            .viewStore(InMemoryViewStore.builder().build())
            .module(
                Module.builder(IngestAggregate.class)
                    .regCmd(INGEST, spec -> spec.handler(IngestHandler.class).build())
                    .regCmd(NOTIFY, spec -> spec.handler(NotifyHandler.class).build())
                    .regApply(INGESTED, IngestAggregate::ingested)
                    .regApply(NOTIFIED, IngestAggregate::notified)
                    .regFx(INGESTED, (ctx, e) -> List.of(new NotifyCommand(UUID.randomUUID())))
                    .regQuery(GET_SOURCE, (ctx, q) -> "source:" + q.id())
                    .build())
            .build();
    return LambdaRuntime.builder(app)
        .sqs()
        .api()
        .s3((bucket, key) -> new IngestCommand(UUID.randomUUID(), bucket + "/" + key))
        .direct()
        .router(router)
        .build();
  }

  private static String sqsEvent(String body) throws Exception {
    return Messages.MAPPER.writeValueAsString(
        Map.of(
            "Records", List.of(Map.of("eventSource", "aws:sqs", "messageId", "m1", "body", body))));
  }

  @Test
  void sqsDispatchesRoutesEffectAndReturnsBatchResponse() throws Exception {
    LambdaRuntime runtime = runtime();
    UUID id = UUID.randomUUID();
    String body =
        Messages.MAPPER.writeValueAsString(
            Map.of("cmdId", "ingest", "command", Map.of("id", id.toString(), "source", "sqs")));

    String result = runtime.handle(sqsEvent(body));

    assertEquals(1, events.load(RequestMeta.DEFAULT_REALM, id).size());
    assertEquals(1, router.commands.size(), "effect routed to the router service");
    assertInstanceOf(NotifyCommand.class, router.commands.getFirst());
    assertEquals(List.of(0, 0), router.metas.getFirst().breadcrumbs());
    assertEquals(
        0, Messages.MAPPER.readTree(result).get("batchItemFailures").size(), "no failures");
  }

  @Test
  void duplicateReturnsStoredEffectsWithoutReprocessing() throws Exception {
    LambdaRuntime runtime = runtime();
    UUID id = UUID.randomUUID();
    RequestMeta meta = RequestMeta.builder().requestId(UUID.randomUUID()).build();
    String body = Messages.encodeCommand(new IngestCommand(id, "sqs"), meta);
    String event = sqsEvent(body);

    runtime.handle(event);
    runtime.handle(event); // same request-id + breadcrumbs -> dedup

    assertEquals(1, events.load(RequestMeta.DEFAULT_REALM, id).size(), "not re-processed");
    assertEquals(
        2, router.commands.size(), "the stored response's effects are re-routed on the duplicate");
  }

  @Test
  void s3MapsBucketKeyToCommand() {
    LambdaRuntime runtime = runtime();
    String event =
        """
                {"Records":[{"eventSource":"aws:s3","s3":{"bucket":{"name":"docs"},"object":{"key":"a/b.csv"}}}]}""";

    runtime.handle(event);

    assertEquals(1, router.commands.size());
    assertInstanceOf(NotifyCommand.class, router.commands.getFirst());
  }

  @Test
  void apiHealthCheckReturns200WithoutDispatch() throws Exception {
    LambdaRuntime runtime = runtime();
    String event =
        Messages.MAPPER.writeValueAsString(
            Map.of("requestContext", Map.of(), "httpMethod", "GET", "path", "/health"));

    JsonNode response = Messages.MAPPER.readTree(runtime.handle(event));

    assertEquals(200, response.get("statusCode").asInt());
    assertTrue(response.get("body").asText().contains("ok"));
    assertEquals(0, router.commands.size(), "health check dispatches nothing");
  }

  @Test
  void apiReturnsHttpResponse() throws Exception {
    LambdaRuntime runtime = runtime();
    UUID id = UUID.randomUUID();
    String body =
        Messages.MAPPER.writeValueAsString(
            Map.of("cmdId", "ingest", "command", Map.of("id", id.toString(), "source", "api")));
    String event =
        Messages.MAPPER.writeValueAsString(Map.of("requestContext", Map.of(), "body", body));

    JsonNode response = Messages.MAPPER.readTree(runtime.handle(event));

    assertEquals(200, response.get("statusCode").asInt());
    assertTrue(response.get("body").asText().contains("success"));
    assertEquals(1, events.load(RequestMeta.DEFAULT_REALM, id).size());
  }

  @Test
  void apiQueryReturnsResult() throws Exception {
    LambdaRuntime runtime = runtime();
    UUID id = UUID.randomUUID();
    String body =
        Messages.MAPPER.writeValueAsString(
            Map.of("queryId", "get-source", "query", Map.of("id", id.toString())));
    String event =
        Messages.MAPPER.writeValueAsString(Map.of("requestContext", Map.of(), "body", body));

    JsonNode response = Messages.MAPPER.readTree(runtime.handle(event));

    assertEquals(200, response.get("statusCode").asInt());
    JsonNode result = Messages.MAPPER.readTree(response.get("body").asText());
    assertEquals("source:" + id, result.get("result").asText());
    assertEquals(0, router.commands.size(), "a query dispatches no command");
  }
}
