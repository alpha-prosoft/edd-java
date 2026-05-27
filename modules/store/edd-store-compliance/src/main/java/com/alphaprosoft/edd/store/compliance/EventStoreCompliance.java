package com.alphaprosoft.edd.store.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.Identity;
import com.alphaprosoft.edd.core.EventMeta;
import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.IdentityConflictException;
import com.alphaprosoft.edd.core.OptimisticLockException;
import com.alphaprosoft.edd.core.StoredEvent;
import com.alphaprosoft.edd.core.TypeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The behavioral contract every {@link EventStore} must satisfy (edd-core's compliance suite, in
 * Java). Subclass it in each store module and implement {@link #newStore()}.
 */
public abstract class EventStoreCompliance {

  protected abstract EventStore newStore();

  public record Recorded(UUID id, String what) implements Event {}

  /**
   * A richer event for payload data-integrity (string, list, map fields through the JSON codec).
   */
  public record Logged(UUID id, String text, List<String> tags, Map<String, String> attrs)
      implements Event {}

  static {
    TypeRegistry.register(TypeRegistry.EVENT, "recorded", Recorded.class);
    TypeRegistry.register(TypeRegistry.EVENT, "logged", Logged.class);
  }

  private static final String REALM = "r1";
  private static final String SERVICE = "svc-a";

  private StoredEvent ev(UUID aggId, long seq, UUID interactionId) {
    EventMeta meta = new EventMeta(Map.of(EventMeta.INTERACTION_ID, interactionId.toString()));
    return new StoredEvent(aggId, seq, new Recorded(aggId, "e" + seq), meta);
  }

  @Test
  void appendsAndLoadsOrdered() {
    EventStore s = newStore();
    UUID id = UUID.randomUUID();
    UUID it = UUID.randomUUID();
    s.append(REALM, id, 0, List.of(ev(id, 1, it), ev(id, 2, it)));
    List<StoredEvent> loaded = s.load(REALM, id);
    assertEquals(List.of(1L, 2L), loaded.stream().map(StoredEvent::eventSeq).toList());
  }

  @Test
  void loadOfMissingAggregateIsEmpty() {
    assertTrue(newStore().load(REALM, UUID.randomUUID()).isEmpty());
  }

  @Test
  void eventPayloadRoundTripsWithSpecialCharsAndNesting() {
    EventStore s = newStore();
    UUID id = UUID.randomUUID();
    Logged event =
        new Logged(
            id,
            "special: @#$%^&*()_+-=[]{}|;':\",./<>?  \n\t  ünïcøde  你好  🎉  \\backslash\\",
            List.of("a", "b", "c"),
            Map.of("k1", "v1", "k2", "v2"));
    EventMeta meta = new EventMeta(Map.of(EventMeta.INTERACTION_ID, UUID.randomUUID().toString()));
    s.append(REALM, id, 0, List.of(new StoredEvent(id, 1, event, meta)));
    List<StoredEvent> loaded = s.load(REALM, id);
    assertEquals(1, loaded.size());
    assertEquals(
        event, loaded.getFirst().event(), "event payload must survive the storage codec exactly");
  }

  @Test
  void deepEventStreamLoadsOrdered() {
    EventStore s = newStore();
    UUID id = UUID.randomUUID();
    UUID it = UUID.randomUUID();
    List<StoredEvent> batch = new ArrayList<>();
    for (long v = 1; v <= 100; v++) {
      batch.add(ev(id, v, it));
    }
    s.append(REALM, id, 0, batch);
    List<StoredEvent> loaded = s.load(REALM, id);
    assertEquals(100, loaded.size());
    assertEquals(1L, loaded.getFirst().eventSeq());
    assertEquals(100L, loaded.getLast().eventSeq());
    assertEquals(100, s.maxEventSeq(REALM, id));
  }

  @Test
  void loadAfterFiltersBySeq() {
    EventStore s = newStore();
    UUID id = UUID.randomUUID();
    UUID it = UUID.randomUUID();
    s.append(REALM, id, 0, List.of(ev(id, 1, it), ev(id, 2, it), ev(id, 3, it)));
    assertEquals(
        List.of(2L, 3L), s.load(REALM, id, 1).stream().map(StoredEvent::eventSeq).toList());
  }

  @Test
  void maxEventSeqIsZeroThenHighest() {
    EventStore s = newStore();
    UUID id = UUID.randomUUID();
    assertEquals(0, s.maxEventSeq(REALM, id));
    s.append(REALM, id, 0, List.of(ev(id, 1, UUID.randomUUID()), ev(id, 2, UUID.randomUUID())));
    assertEquals(2, s.maxEventSeq(REALM, id));
  }

  @Test
  void optimisticLockRejectsWrongExpectedVersion() {
    EventStore s = newStore();
    UUID id = UUID.randomUUID();
    s.append(REALM, id, 0, List.of(ev(id, 1, UUID.randomUUID())));
    assertThrows(
        Exception.class, () -> s.append(REALM, id, 0, List.of(ev(id, 2, UUID.randomUUID()))));
  }

  @Test
  void realmIsolation() {
    EventStore s = newStore();
    UUID id = UUID.randomUUID();
    s.append("a", id, 0, List.of(ev(id, 1, UUID.randomUUID())));
    s.append("b", id, 0, List.of(ev(id, 1, UUID.randomUUID())));
    assertEquals(1, s.load("a", id).size());
    assertEquals(1, s.load("b", id).size());
  }

  @Test
  void identityReserveLookupServiceScopingAndBulk() {
    EventStore s = newStore();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    String alice =
        "alice-" + UUID.randomUUID(); // unique so the suite is isolated on a shared store
    String bob = "bob-" + UUID.randomUUID();
    s.reserveIdentities(REALM, SERVICE, List.of(new Identity(alice, a), new Identity(bob, b)));
    assertEquals(a, s.aggregateIdByIdentity(REALM, SERVICE, alice).orElseThrow());
    assertTrue(
        s.aggregateIdByIdentity(REALM, "other-svc", alice).isEmpty(),
        "identities are service-scoped");
    assertEquals(
        Map.of(alice, a, bob, b),
        s.aggregateIdByIdentity(
            REALM, SERVICE, List.of(alice, bob, "missing-" + UUID.randomUUID())));
  }

  @Test
  void duplicateIdentityConflicts() {
    EventStore s = newStore();
    String name = "dup-" + UUID.randomUUID();
    s.reserveIdentities(REALM, SERVICE, List.of(new Identity(name, UUID.randomUUID())));
    assertThrows(
        IdentityConflictException.class,
        () -> s.reserveIdentities(REALM, SERVICE, List.of(new Identity(name, UUID.randomUUID()))));
  }

  @Test
  void eventsGroupedByInteraction() {
    EventStore s = newStore();
    UUID it = UUID.randomUUID();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    s.append(REALM, a, 0, List.of(ev(a, 1, it)));
    s.append(REALM, b, 0, List.of(ev(b, 1, it), ev(b, 2, UUID.randomUUID())));
    assertEquals(2, s.eventsByInteraction(REALM, it).size());
  }

  @Test
  void idempotencyByRequestIdAndBreadcrumbs() {
    EventStore s = newStore();
    UUID req = UUID.randomUUID();
    List<Integer> root = List.of(0);
    assertTrue(s.findResponse(REALM, req, root).isEmpty());
    CommandResponse resp =
        new CommandResponse.Success(UUID.randomUUID(), List.of(), List.of(), List.of());
    s.logResponse(REALM, req, root, resp);
    assertEquals(resp, s.findResponse(REALM, req, root).orElseThrow());
    assertTrue(
        s.findResponse(REALM, req, List.of(0, 1)).isEmpty(),
        "different breadcrumbs ⇒ distinct entry");
    assertTrue(
        s.findResponse(REALM, UUID.randomUUID(), root).isEmpty(),
        "different request ⇒ distinct entry");
  }

  @Test
  void appendBatchPersistsEventsAndIdentitiesAcrossAggregates() {
    EventStore s = newStore();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID it = UUID.randomUUID();
    String name = "batch-" + UUID.randomUUID();
    s.appendBatch(
        REALM,
        SERVICE,
        List.of(ev(a, 1, it), ev(a, 2, it), ev(b, 1, it)),
        List.of(new Identity(name, a)));
    assertEquals(2, s.load(REALM, a).size());
    assertEquals(1, s.load(REALM, b).size());
    assertEquals(a, s.aggregateIdByIdentity(REALM, SERVICE, name).orElseThrow());
  }

  @Test
  void appendBatchIsAtomicOnSeqConflict() {
    EventStore s = newStore();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    s.appendBatch(REALM, SERVICE, List.of(ev(a, 1, UUID.randomUUID())), List.of());
    // a@1 already exists -> the whole batch (including b) must roll back
    assertThrows(
        OptimisticLockException.class,
        () ->
            s.appendBatch(
                REALM,
                SERVICE,
                List.of(ev(b, 1, UUID.randomUUID()), ev(a, 1, UUID.randomUUID())),
                List.of()));
    assertTrue(s.load(REALM, b).isEmpty(), "nothing committed when the batch fails");
  }

  @Test
  void appendBatchIsAtomicOnIdentityConflict() {
    EventStore s = newStore();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    String name = "taken-" + UUID.randomUUID();
    s.appendBatch(REALM, SERVICE, List.of(), List.of(new Identity(name, a)));
    assertThrows(
        IdentityConflictException.class,
        () ->
            s.appendBatch(
                REALM,
                SERVICE,
                List.of(ev(b, 1, UUID.randomUUID())),
                List.of(new Identity(name, b))));
    assertTrue(
        s.load(REALM, b).isEmpty(), "events roll back when an identity in the batch conflicts");
  }

  @Test
  void requestAndErrorLogsDoNotThrow() {
    EventStore s = newStore();
    UUID req = UUID.randomUUID();
    s.logRequest(REALM, req, List.of(0), "body");
    s.logError(REALM, req, List.of(0), "boom");
  }
}
