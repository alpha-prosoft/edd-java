package com.alphaprosoft.edd.counter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.EventMeta;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.core.StoredEvent;
import com.alphaprosoft.edd.core.User;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayAndStoreTest {

  private static Application appWith(InMemoryEventStore store) {
    return Application.builder("counter-svc")
        .eventStore(store)
        .viewStore(InMemoryViewStore.builder().build())
        .module(
            Module.builder(CounterAggregate.class)
                .regCmd(
                    CounterRegistry.INCREMENT, spec -> spec.handler(IncrementHandler.class).build())
                .regCmd(
                    CounterRegistry.CLAIM_NAME,
                    spec -> spec.handler(ClaimNameHandler.class).build())
                .regApply(CounterRegistry.INCREMENTED, CounterAggregate::incremented)
                .regApply(CounterRegistry.NAME_CLAIMED, CounterAggregate::nameClaimed)
                .build())
        .build();
  }

  @Test
  void replayAccumulatesStateAndAssignsContiguousSeq() {
    InMemoryEventStore store = InMemoryEventStore.builder().build();
    Application app = appWith(store);
    UUID id = UUID.randomUUID();

    app.dispatch(IncrementCommand.of(id, 5), RequestMeta.newRequest());
    app.dispatch(IncrementCommand.of(id, 3), RequestMeta.newRequest());

    List<StoredEvent> stored = store.load(RequestMeta.DEFAULT_REALM, id);
    assertEquals(2, stored.size());
    assertEquals(1, stored.get(0).eventSeq());
    assertEquals(2, stored.get(1).eventSeq());

    // the second handler saw the replayed state from the first event
    IncrementedEvent second = assertInstanceOf(IncrementedEvent.class, stored.get(1).event());
    assertEquals(5, second.priorCount(), "replay should reflect the first increment");
  }

  @Test
  void aggregateVisibleToHandlerGrowsWithEachDispatch() {
    InMemoryEventStore store = InMemoryEventStore.builder().build();
    Application app = appWith(store);
    UUID id = UUID.randomUUID();

    app.dispatch(IncrementCommand.of(id, 2), RequestMeta.newRequest());
    app.dispatch(IncrementCommand.of(id, 4), RequestMeta.newRequest());
    var resp = app.dispatch(IncrementCommand.of(id, 1), RequestMeta.newRequest());

    var success = assertInstanceOf(CommandResponse.Success.class, resp);
    IncrementedEvent third = assertInstanceOf(IncrementedEvent.class, success.events().getFirst());
    assertEquals(
        6, third.priorCount(), "ctx.aggregate() reflects 2 + 4 before the third increment");
  }

  @Test
  void eventsAreStampedWithProvenanceAndCustomAnnotations() {
    InMemoryEventStore store = InMemoryEventStore.builder().build();
    Application app = appWith(store);
    UUID id = UUID.randomUUID();
    RequestMeta meta =
        RequestMeta.builder()
            .realm("tenant-x")
            .user(User.of("u-1", "counter-admin"))
            .annotation("source", "batch-import")
            .build();

    app.dispatch(IncrementCommand.of(id, 1), meta);

    EventMeta stored = store.load("tenant-x", id).getFirst().meta();
    assertEquals("tenant-x", stored.get(EventMeta.REALM));
    assertEquals("u-1", stored.get(EventMeta.USER_ID));
    assertEquals("counter-admin", stored.get(EventMeta.ROLE));
    assertEquals(meta.requestId().toString(), stored.get(EventMeta.REQUEST_ID));
    assertNotNull(stored.get(EventMeta.CREATED_ON));
    assertEquals(
        "batch-import", stored.get("source"), "custom request annotations propagate to events");
  }

  @Test
  void realmIsolatesEventStreams() {
    InMemoryEventStore store = InMemoryEventStore.builder().build();
    Application app = appWith(store);
    UUID id = UUID.randomUUID();

    app.dispatch(IncrementCommand.of(id, 1), RequestMeta.builder().realm("a").build());
    app.dispatch(IncrementCommand.of(id, 1), RequestMeta.builder().realm("b").build());

    assertEquals(1, store.load("a", id).size());
    assertEquals(1, store.load("b", id).size());
  }

  @Test
  void staleVersionIsRejectedWithConcurrentModification() {
    InMemoryEventStore store = InMemoryEventStore.builder().build();
    Application app = appWith(store);
    UUID id = UUID.randomUUID();

    app.dispatch(IncrementCommand.of(id, 5), RequestMeta.newRequest()); // version -> 1

    var stale = app.dispatch(new IncrementCommand(id, 1, 0L), RequestMeta.newRequest());
    var failure = assertInstanceOf(CommandResponse.Failure.class, stale);
    assertEquals("concurrent-modification", failure.code());

    var ok = app.dispatch(new IncrementCommand(id, 1, 1L), RequestMeta.newRequest());
    assertInstanceOf(CommandResponse.Success.class, ok);
  }

  @Test
  void duplicateIdentityIsRejected() {
    InMemoryEventStore store = InMemoryEventStore.builder().build();
    Application app = appWith(store);

    var first =
        app.dispatch(new ClaimNameCommand(UUID.randomUUID(), "alice"), RequestMeta.newRequest());
    var firstSuccess = assertInstanceOf(CommandResponse.Success.class, first);
    UUID firstId = firstSuccess.aggregateId();

    var second =
        app.dispatch(new ClaimNameCommand(UUID.randomUUID(), "alice"), RequestMeta.newRequest());
    var failure = assertInstanceOf(CommandResponse.Failure.class, second);
    assertEquals("identity-conflict", failure.code());

    assertEquals(
        firstId,
        store
            .aggregateIdByIdentity(RequestMeta.DEFAULT_REALM, "counter-svc", "alice")
            .orElseThrow());
  }

  @Test
  void rejectionStoresNothing() {
    InMemoryEventStore store = InMemoryEventStore.builder().build();
    Application app = appWith(store);
    UUID id = UUID.randomUUID();

    var resp = app.dispatch(IncrementCommand.of(id, 0), RequestMeta.newRequest());

    assertInstanceOf(CommandResponse.Failure.class, resp);
    assertTrue(
        store.load(RequestMeta.DEFAULT_REALM, id).isEmpty(),
        "rejected command must not append events");
  }
}
