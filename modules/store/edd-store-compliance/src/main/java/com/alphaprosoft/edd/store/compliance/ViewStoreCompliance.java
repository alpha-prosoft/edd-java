package com.alphaprosoft.edd.store.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.TypeRegistry;
import com.alphaprosoft.edd.core.ViewStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The behavioral contract every {@link ViewStore} must satisfy. Subclass it per store module and
 * implement {@link #newStore()}. Ported from edd-core's view-store compliance, adapted to
 * edd-java's typed-record model: basic + versioned snapshots, version history, realm AND service
 * isolation, data integrity (special characters, complex nesting, large + minimal payloads), and
 * strict validation.
 */
public abstract class ViewStoreCompliance {

  protected abstract ViewStore newStore();

  /**
   * A store scoped to {@code service} but sharing the same backend as other services — so two
   * services that reuse one aggregate id never collide. Stores whose instances are inherently
   * isolated (e.g. in-memory: a fresh map per instance) may use the default; stores backed by a
   * shared bucket/table/DB must override to bind the service into the key.
   */
  protected ViewStore newStore(String service) {
    return newStore();
  }

  public record Counter(UUID id, long version, long count) implements Aggregate {}

  /**
   * A plain nested record (not an aggregate) — used to exercise nested/collection serialization.
   */
  public record Item(int n, String label) {}

  /** A richer aggregate for the data-integrity tests (string, list, map fields). */
  public record Doc(UUID id, long version, String text, List<Item> items, Map<String, String> meta)
      implements Aggregate {}

  static {
    TypeRegistry.register(TypeRegistry.AGGREGATE, "Counter", Counter.class);
    TypeRegistry.register(TypeRegistry.AGGREGATE, "Doc", Doc.class);
  }

  private static final String REALM = "r1";

  // ---- Basic snapshot operations -----------------------------------------

  @Test
  void updateThenLatestSnapshot() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update(REALM, new Counter(id, 1, 10));
    s.update(REALM, new Counter(id, 2, 20));
    Counter got = s.<Counter>getSnapshot(REALM, id).orElseThrow();
    assertEquals(2, got.version());
    assertEquals(20, got.count());
  }

  @Test
  void missingIdIsEmpty() {
    assertTrue(newStore().getSnapshot(REALM, UUID.randomUUID()).isEmpty());
  }

  @Test
  void multipleAggregatesIsolated() {
    ViewStore s = newStore();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    s.update(REALM, new Counter(a, 1, 1));
    s.update(REALM, new Counter(b, 1, 2));
    s.update(REALM, new Counter(c, 1, 3));
    assertEquals(1, s.<Counter>getSnapshot(REALM, a).orElseThrow().count());
    assertEquals(2, s.<Counter>getSnapshot(REALM, b).orElseThrow().count());
    assertEquals(3, s.<Counter>getSnapshot(REALM, c).orElseThrow().count());
  }

  @Test
  void latestIsHighestAcrossManyVersions() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    for (long v = 1; v <= 5; v++) {
      s.update(REALM, new Counter(id, v, v * 10));
    }
    Counter latest = s.<Counter>getSnapshot(REALM, id).orElseThrow();
    assertEquals(5, latest.version());
    assertEquals(50, latest.count());
  }

  // ---- Versioned snapshot retrieval (history) ----------------------------

  @Test
  void exactVersionSnapshotFromHistory() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update(REALM, new Counter(id, 1, 10));
    s.update(REALM, new Counter(id, 2, 20));
    assertEquals(10, s.<Counter>getSnapshot(REALM, id, 1).orElseThrow().count());
    assertEquals(20, s.<Counter>getSnapshot(REALM, id, 2).orElseThrow().count());
    assertTrue(s.getSnapshot(REALM, id, 99).isEmpty(), "never-stored version ⇒ empty");
  }

  @Test
  void versionForMissingIdIsEmpty() {
    assertTrue(newStore().getSnapshot(REALM, UUID.randomUUID(), 1).isEmpty());
  }

  @Test
  void nonExistentPositiveVersionIsEmpty() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update(REALM, new Counter(id, 1, 1));
    s.update(REALM, new Counter(id, 3, 3));
    assertTrue(s.getSnapshot(REALM, id, 2).isEmpty(), "never-stored version 2 ⇒ empty");
    assertEquals(3, s.<Counter>getSnapshot(REALM, id).orElseThrow().version());
  }

  @Test
  void deepVersionHistory() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    for (long v = 1; v <= 100; v++) {
      s.update(REALM, new Counter(id, v, v));
    }
    assertEquals(1, s.<Counter>getSnapshot(REALM, id, 1).orElseThrow().count());
    assertEquals(50, s.<Counter>getSnapshot(REALM, id, 50).orElseThrow().count());
    assertEquals(100, s.<Counter>getSnapshot(REALM, id, 100).orElseThrow().count());
    assertEquals(100, s.<Counter>getSnapshot(REALM, id).orElseThrow().version());
  }

  @Test
  void historicalVersionsAreImmutable() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update(REALM, new Doc(id, 1, "v1", List.of(new Item(1, "a")), Map.of("k", "1")));
    s.update(
        REALM, new Doc(id, 2, "v2", List.of(new Item(1, "a"), new Item(2, "b")), Map.of("k", "2")));
    Doc v1 = s.<Doc>getSnapshot(REALM, id, 1).orElseThrow();
    assertEquals("v1", v1.text());
    assertEquals(1, v1.items().size(), "v1 must be unaffected by the v2 update");
    Doc v2 = s.<Doc>getSnapshot(REALM, id, 2).orElseThrow();
    assertEquals("v2", v2.text());
    assertEquals(2, v2.items().size());
  }

  // ---- Data integrity ----------------------------------------------------

  @Test
  void idPreservedExactly() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update(REALM, new Counter(id, 1, 1));
    assertEquals(id, s.<Counter>getSnapshot(REALM, id).orElseThrow().id());
  }

  @Test
  void specialCharactersPreserved() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    String text =
        "special: @#$%^&*()_+-=[]{}|;':\",./<>?  \n\t  ünïcøde  你好  🎉  \\backslash\\  \"quoted\"";
    s.update(REALM, new Doc(id, 1, text, List.of(), Map.of()));
    assertEquals(text, s.<Doc>getSnapshot(REALM, id).orElseThrow().text());
  }

  @Test
  void complexNestedDataPreserved() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    Doc doc =
        new Doc(
            id,
            1,
            "nested",
            List.of(new Item(1, "alice"), new Item(2, "bob")),
            Map.of("theme", "dark", "lang", "en"));
    s.update(REALM, doc);
    assertEquals(doc, s.<Doc>getSnapshot(REALM, id).orElseThrow());
  }

  @Test
  void largePayloadPreserved() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    List<Item> items = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      items.add(new Item(i, "item-" + i));
    }
    s.update(REALM, new Doc(id, 1, "big", List.copyOf(items), Map.of()));
    Doc got = s.<Doc>getSnapshot(REALM, id).orElseThrow();
    assertEquals(1000, got.items().size());
    assertEquals(items, got.items());
  }

  @Test
  void minimalAggregate() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update(REALM, new Counter(id, 1, 0));
    Counter got = s.<Counter>getSnapshot(REALM, id).orElseThrow();
    assertEquals(id, got.id());
    assertEquals(1, got.version());
  }

  // ---- Realm + service isolation -----------------------------------------

  @Test
  void realmIsolation() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update("a", new Counter(id, 1, 1));
    assertTrue(s.getSnapshot("b", id).isEmpty());
  }

  @Test
  void sameIdDifferentRealmsIsolated() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update("realm-a", new Counter(id, 1, 100));
    s.update("realm-b", new Counter(id, 2, 200));
    assertEquals(100, s.<Counter>getSnapshot("realm-a", id).orElseThrow().count());
    assertEquals(1, s.<Counter>getSnapshot("realm-a", id).orElseThrow().version());
    assertEquals(200, s.<Counter>getSnapshot("realm-b", id).orElseThrow().count());
    assertEquals(2, s.<Counter>getSnapshot("realm-b", id).orElseThrow().version());
  }

  @Test
  void serviceIsolation() {
    // Two services sharing one backend must not collide on the same aggregate id (and realm).
    // A view store that drops the service from its key passes every other test yet fails here.
    ViewStore a = newStore("svc-alpha");
    ViewStore b = newStore("svc-beta");
    UUID id = UUID.randomUUID();
    a.update(REALM, new Counter(id, 1, 100));
    b.update(REALM, new Counter(id, 1, 200));
    assertEquals(
        100,
        a.<Counter>getSnapshot(REALM, id).orElseThrow().count(),
        "service alpha must not see service beta's aggregate at the same id");
    assertEquals(200, b.<Counter>getSnapshot(REALM, id).orElseThrow().count());
  }

  // ---- Strict validation -------------------------------------------------

  @Test
  void rejectsInvalidAggregates() {
    ViewStore s = newStore();
    assertThrows(RuntimeException.class, () -> s.update(REALM, null));
    assertThrows(
        RuntimeException.class, () -> s.update(REALM, new Counter(UUID.randomUUID(), 0, 0)));
    assertThrows(
        RuntimeException.class, () -> s.update(REALM, new Counter(UUID.randomUUID(), -1, 0)));
  }

  @Test
  void versionZeroQueryThrows() {
    ViewStore s = newStore();
    UUID id = UUID.randomUUID();
    s.update(REALM, new Counter(id, 1, 1));
    assertThrows(
        RuntimeException.class, () -> s.getSnapshot(REALM, id, 0), "version 0 query must throw");
    assertThrows(
        RuntimeException.class,
        () -> s.getSnapshot(REALM, id, -1),
        "negative version query must throw");
  }
}
