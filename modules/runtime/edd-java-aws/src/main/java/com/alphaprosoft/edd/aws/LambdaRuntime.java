package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight runtime core. Picks the {@link IngestFilter} that handles the event and lets it shape
 * the infra response. The shared {@link Processor} it hands the filter dispatches a command and
 * sends any effects to the {@link Router} (the router service). Effects are <em>not</em> run
 * in-process — they go to the router and return as new invocations. On a duplicate, {@code
 * app.dispatch} returns the stored response (with its effects from the DB), so the same effects are
 * routed again — no S3 response cache. (In-process saga for tests lives in {@code InProcessSaga},
 * edd-java-testkit.)
 */
public final class LambdaRuntime {

  private final Application app;
  private final List<IngestFilter> filters;
  private final Router router;

  private LambdaRuntime(Builder b) {
    this.app = b.app;
    this.filters = List.copyOf(b.filters);
    this.router = b.router;
  }

  public static Builder builder(Application app) {
    return new Builder(app);
  }

  private CommandResponse dispatchAndRoute(Command command, RequestMeta meta) {
    CommandResponse response = app.dispatch(command, meta);
    if (response instanceof CommandResponse.Success success && router != null) {
      int index = 0;
      for (Command effect : success.effects()) {
        List<Integer> crumbs = new ArrayList<>(meta.breadcrumbs());
        crumbs.add(index++);
        router.route(effect, RequestMeta.builder(meta).breadcrumbs(crumbs).build());
      }
    }
    return response;
  }

  public String handle(String eventJson) {
    JsonNode event = Messages.parse(eventJson);
    IngestFilter filter =
        filters.stream()
            .filter(f -> f.handles(event))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No filter handles the event"));
    try {
      return Messages.MAPPER.writeValueAsString(filter.process(event, this::dispatchAndRoute));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write response", e);
    }
  }

  public void handle(InputStream input, OutputStream output) throws IOException {
    output.write(
        handle(new String(input.readAllBytes(), StandardCharsets.UTF_8))
            .getBytes(StandardCharsets.UTF_8));
  }

  public static final class Builder {
    private final Application app;
    private final List<IngestFilter> filters = new ArrayList<>();
    private Router router;

    private Builder(Application app) {
      this.app = app;
    }

    public Builder filter(IngestFilter filter) {
      filters.add(filter);
      return this;
    }

    public Builder sqs() {
      return filter(new SqsFilter());
    }

    public Builder api() {
      return filter(new ApiFilter(this::queryViaApp));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object queryViaApp(String queryId, JsonNode query, RequestMeta meta) {
      QueryId<?, ?> id =
          QueryId.lookup(queryId)
              .orElseThrow(() -> new IllegalStateException("Unknown queryId: " + queryId));
      Query q = (Query) Messages.MAPPER.convertValue(query, id.queryType());
      return app.query((QueryId) id, q, meta);
    }

    public Builder s3(S3CommandMapper mapper) {
      return filter(new S3Filter(mapper));
    }

    public Builder direct() {
      return filter(new DirectFilter());
    }

    public Builder router(Router router) {
      this.router = router;
      return this;
    }

    public Builder router(String routerQueueUrl) {
      return router(new SqsRouter(routerQueueUrl));
    }

    public LambdaRuntime build() {
      return new LambdaRuntime(this);
    }
  }
}
