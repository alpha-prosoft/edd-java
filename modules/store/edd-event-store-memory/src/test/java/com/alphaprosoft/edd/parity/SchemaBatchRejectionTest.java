package com.alphaprosoft.edd.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.alphaprosoft.edd.core.Schema;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchemaBatchRejectionTest {

  record DepositCmd(UUID id, long amount) implements Command {}

  record WithdrawCmd(UUID id, long amount) implements Command {}

  record RejectingCmd(UUID id) implements Command {}

  record Deposited(UUID id, long amount) implements Event {}

  record Withdrawn(UUID id, long amount) implements Event {}

  record Wallet(UUID id, long version, long balance) implements Aggregate {
    static Wallet deposited(Wallet w, Deposited e) {
      long balance = Optional.ofNullable(w).map(Wallet::balance).orElse(0L) + e.amount();
      return new Wallet(e.id(), 0, balance);
    }

    static Wallet withdrawn(Wallet w, Withdrawn e) {
      long balance = Optional.ofNullable(w).map(Wallet::balance).orElse(0L) - e.amount();
      return new Wallet(e.id(), 0, balance);
    }
  }

  record Probe(UUID id, long value) implements Query {}

  public static final class DepositHandler implements CommandHandler<DepositCmd, Wallet> {
    @Override
    public List<CommandEmission> handle(CommandContext<Wallet> ctx, DepositCmd cmd) {
      return List.of(new Deposited(cmd.id(), cmd.amount()));
    }
  }

  public static final class WithdrawHandler implements CommandHandler<WithdrawCmd, Wallet> {
    @Override
    public List<CommandEmission> handle(CommandContext<Wallet> ctx, WithdrawCmd cmd) {
      return List.of(new Withdrawn(cmd.id(), cmd.amount()));
    }
  }

  public static final class RejectingHandler implements CommandHandler<RejectingCmd, Wallet> {
    @Override
    public List<CommandEmission> handle(CommandContext<Wallet> ctx, RejectingCmd cmd) {
      return List.of(Rejection.of("first-problem"), Rejection.of("second-problem"));
    }
  }

  static final CommandId<DepositCmd> DEPOSIT = CommandId.of("wallet-deposit", DepositCmd.class);
  static final CommandId<WithdrawCmd> WITHDRAW = CommandId.of("wallet-withdraw", WithdrawCmd.class);
  static final CommandId<RejectingCmd> REJECT = CommandId.of("wallet-reject", RejectingCmd.class);
  static final EventId<Deposited> DEPOSITED = EventId.of("wallet-deposited", Deposited.class);
  static final EventId<Withdrawn> WITHDRAWN = EventId.of("wallet-withdrawn", Withdrawn.class);
  static final QueryId<Probe, Long> PROBE = QueryId.of("wallet-probe", Probe.class, Long.class);

  private Application app() {
    return Application.builder("wallet-svc")
        .eventStore(InMemoryEventStore.builder().build())
        .viewStore(InMemoryViewStore.builder().build())
        .module(
            Module.builder(Wallet.class)
                .regCmd(
                    DEPOSIT,
                    spec ->
                        spec.handler(DepositHandler.class)
                            .consumes(
                                Schema.require(c -> c.amount() > 0, "amount must be positive"))
                            .build())
                .regCmd(WITHDRAW, spec -> spec.handler(WithdrawHandler.class).build())
                .regCmd(REJECT, spec -> spec.handler(RejectingHandler.class).build())
                .regApply(DEPOSITED, Wallet::deposited)
                .regApply(WITHDRAWN, Wallet::withdrawn)
                .validate(Schema.require(w -> w.balance() >= 0, "balance must not be negative"))
                .build())
        .regQuery(
            PROBE,
            spec ->
                spec.handler((ctx, q) -> q.value())
                    .produces(Schema.require(v -> v >= 0, "value must not be negative"))
                    .build())
        .build();
  }

  @Test
  void consumesRejectsInvalidCommand() {
    CommandResponse resp =
        app().dispatch(new DepositCmd(UUID.randomUUID(), -5), RequestMeta.newRequest());
    CommandResponse.Failure f = assertInstanceOf(CommandResponse.Failure.class, resp);
    assertEquals("invalid-command", f.code());
    assertEquals(List.of("amount must be positive"), f.details().get("violations"));
  }

  @Test
  void aggregateStateValidationRejectsOverdraw() {
    Application app = app();
    UUID id = UUID.randomUUID();
    assertInstanceOf(
        CommandResponse.Success.class,
        app.dispatch(new DepositCmd(id, 100), RequestMeta.newRequest()));

    CommandResponse resp = app.dispatch(new WithdrawCmd(id, 250), RequestMeta.newRequest());

    CommandResponse.Failure f = assertInstanceOf(CommandResponse.Failure.class, resp);
    assertEquals("invalid-state", f.code());
    assertEquals(
        1,
        app.eventStore().load(RequestMeta.DEFAULT_REALM, id).size(),
        "overdraw event not stored");
  }

  @Test
  void collectsAllRejections() {
    CommandResponse resp =
        app().dispatch(new RejectingCmd(UUID.randomUUID()), RequestMeta.newRequest());
    CommandResponse.Failure f = assertInstanceOf(CommandResponse.Failure.class, resp);
    assertEquals("rejected", f.code());
    assertEquals(2, f.rejections().size());
    assertEquals("first-problem", f.rejections().get(0).code());
    assertEquals("second-problem", f.rejections().get(1).code());
  }

  @Test
  void producesValidationFailsOnBadResult() {
    Application app = app();
    assertEquals(7L, app.query(PROBE, new Probe(UUID.randomUUID(), 7), RequestMeta.newRequest()));
    assertThrows(
        IllegalStateException.class,
        () -> app.query(PROBE, new Probe(UUID.randomUUID(), -1), RequestMeta.newRequest()));
  }

  @Test
  void batchDispatchProcessesEachWithDistinctBreadcrumbs() {
    Application app = app();
    UUID id = UUID.randomUUID();

    List<CommandResponse> responses =
        app.dispatch(
            List.of(new DepositCmd(id, 10), new DepositCmd(id, 5)), RequestMeta.newRequest());

    assertEquals(2, responses.size());
    assertTrue(responses.stream().allMatch(r -> r instanceof CommandResponse.Success));
    assertEquals(2, app.eventStore().load(RequestMeta.DEFAULT_REALM, id).size());
  }

  @Test
  void batchCarriesAggregateStateAcrossCommandsInOneRequest() {
    Application app = app();
    UUID id = UUID.randomUUID();

    // withdraw 30 only succeeds (balance stays >= 0) if the second command sees the first's +100
    List<CommandResponse> responses =
        app.dispatch(
            List.of(new DepositCmd(id, 100), new WithdrawCmd(id, 30)), RequestMeta.newRequest());

    assertTrue(
        responses.stream().allMatch(r -> r instanceof CommandResponse.Success),
        responses.toString());
    Wallet snapshot =
        app.viewStore().<Wallet>getSnapshot(RequestMeta.DEFAULT_REALM, id).orElseThrow();
    assertEquals(70, snapshot.balance());
    assertEquals(2, app.eventStore().load(RequestMeta.DEFAULT_REALM, id).size());
  }

  @Test
  void batchIsAllOrNothing() {
    Application app = app();
    UUID id = UUID.randomUUID();

    // second command overdraws (100 - 250 < 0) -> invalid-state -> whole request aborts, nothing
    // persisted
    List<CommandResponse> responses =
        app.dispatch(
            List.of(new DepositCmd(id, 100), new WithdrawCmd(id, 250)), RequestMeta.newRequest());

    assertEquals("batch-aborted", ((CommandResponse.Failure) responses.get(0)).code());
    assertEquals("invalid-state", ((CommandResponse.Failure) responses.get(1)).code());
    assertEquals(
        0, app.eventStore().load(RequestMeta.DEFAULT_REALM, id).size(), "nothing committed");
    assertTrue(app.viewStore().getSnapshot(RequestMeta.DEFAULT_REALM, id).isEmpty());
  }

  @Test
  void crossAggregateBatchCommitsAtomically() {
    Application app = app();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();

    List<CommandResponse> responses =
        app.dispatch(
            List.of(new DepositCmd(a, 10), new DepositCmd(b, 20)), RequestMeta.newRequest());

    assertTrue(responses.stream().allMatch(r -> r instanceof CommandResponse.Success));
    assertEquals(
        10,
        app.viewStore().<Wallet>getSnapshot(RequestMeta.DEFAULT_REALM, a).orElseThrow().balance());
    assertEquals(
        20,
        app.viewStore().<Wallet>getSnapshot(RequestMeta.DEFAULT_REALM, b).orElseThrow().balance());
  }
}
