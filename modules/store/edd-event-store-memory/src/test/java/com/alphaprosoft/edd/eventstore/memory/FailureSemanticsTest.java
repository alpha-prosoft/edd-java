package com.alphaprosoft.edd.eventstore.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.command.Rejection;
import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RequestMeta;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Failure-path semantics: response logging, version preconditions, id registry strictness. */
class FailureSemanticsTest {

  record Set(UUID id, Long version, long value) implements Command {}

  record ValueSet(UUID id, long value) implements Event {}

  record Box(UUID id, long version, long value) implements Aggregate {
    static Box set(Box a, ValueSet e) {
      return new Box(e.id(), 0, e.value());
    }
  }

  static final CommandId<Set> SET = CommandId.of("failure-semantics-set", Set.class);
  static final EventId<ValueSet> VALUE_SET =
      EventId.of("failure-semantics-value-set", ValueSet.class);

  public static final class SetHandler implements CommandHandler<Set, Box> {
    static final AtomicInteger RUNS = new AtomicInteger();

    public SetHandler() {}

    @Override
    public List<CommandEmission> handle(CommandContext<Box> ctx, Set cmd) {
      RUNS.incrementAndGet();
      if (cmd.value() < 0) {
        return List.of(Rejection.of("negative-value"));
      }
      return List.of(new ValueSet(cmd.id(), cmd.value()));
    }
  }

  private static Application app() {
    return Application.builder("failure-semantics-svc")
        .eventStore(InMemoryEventStore.builder().build())
        .module(
            Module.builder(Box.class)
                .regCmd(SET, spec -> spec.handler(SetHandler.class).build())
                .regApply(VALUE_SET, Box::set)
                .build())
        .build();
  }

  @Test
  void versionPreconditionAgainstMissingAggregateConflicts() {
    Application app = app();

    CommandResponse resp =
        app.dispatch(new Set(UUID.randomUUID(), 5L, 1), RequestMeta.newRequest());

    CommandResponse.Failure f = assertInstanceOf(CommandResponse.Failure.class, resp);
    assertEquals("concurrent-modification", f.code());
    assertEquals(5L, f.details().get("expected"));
    assertEquals(0L, f.details().get("actual"));
  }

  @Test
  void createWithExpectedVersionZeroSucceeds() {
    Application app = app();

    CommandResponse resp =
        app.dispatch(new Set(UUID.randomUUID(), 0L, 1), RequestMeta.newRequest());

    assertInstanceOf(CommandResponse.Success.class, resp, "version 0 asserts 'not yet created'");
  }

  @Test
  void versionConflictIsNotLoggedSoRedeliveryReprocesses() {
    Application app = app();
    UUID id = UUID.randomUUID();
    RequestMeta meta = RequestMeta.newRequest();

    CommandResponse first = app.dispatch(new Set(id, 1L, 5), meta);
    assertEquals(
        "concurrent-modification", assertInstanceOf(CommandResponse.Failure.class, first).code());

    app.dispatch(new Set(id, null, 1), RequestMeta.newRequest()); // -> version 1

    CommandResponse retry = app.dispatch(new Set(id, 1L, 5), meta); // redelivery, same requestId
    assertInstanceOf(
        CommandResponse.Success.class, retry, "transient conflict reprocessed, not replayed");
  }

  @Test
  void versionPreconditionAgainstExistingAggregateStillWorks() {
    Application app = app();
    UUID id = UUID.randomUUID();
    app.dispatch(new Set(id, null, 1), RequestMeta.newRequest()); // -> version 1

    CommandResponse ok = app.dispatch(new Set(id, 1L, 2), RequestMeta.newRequest());
    assertInstanceOf(CommandResponse.Success.class, ok);

    CommandResponse stale = app.dispatch(new Set(id, 1L, 3), RequestMeta.newRequest());
    CommandResponse.Failure f = assertInstanceOf(CommandResponse.Failure.class, stale);
    assertEquals("concurrent-modification", f.code());
  }

  @Test
  void failureResponseIsLoggedSoRedeliveryReplaysIt() {
    Application app = app();
    SetHandler.RUNS.set(0);
    RequestMeta meta = RequestMeta.newRequest();
    Set rejected = new Set(UUID.randomUUID(), null, -1);

    CommandResponse first = app.dispatch(rejected, meta);
    assertEquals("negative-value", assertInstanceOf(CommandResponse.Failure.class, first).code());
    assertEquals(1, SetHandler.RUNS.get());

    CommandResponse replay = app.dispatch(rejected, meta); // same requestId = redelivery
    assertEquals(first, replay, "redelivery replays the stored failure");
    assertEquals(1, SetHandler.RUNS.get(), "handler did not run again");
  }

  @Test
  void registeringSameTypeUnderTwoIdsThrows() {
    assertThrows(
        IllegalStateException.class, () -> CommandId.of("failure-semantics-set-alias", Set.class));
    assertThrows(
        IllegalStateException.class,
        () -> EventId.of("failure-semantics-value-set-alias", ValueSet.class));
  }

  @Test
  void registeringSameIdWithDifferentTypeThrows() {
    record Other(UUID id) implements Command {}
    assertThrows(
        IllegalStateException.class, () -> CommandId.of("failure-semantics-set", Other.class));
  }
}
