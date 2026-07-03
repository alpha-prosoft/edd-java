package com.alphaprosoft.edd.viewstore.memory;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.ViewStore;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Thread-safe in-memory {@link ViewStore} implementing the compliance contract: version history,
 * highest-version + exact-version snapshots, realm isolation, and strict validation. Realm-scoped.
 */
public final class InMemoryViewStore implements ViewStore {

  // realm -> aggregateId -> (version -> aggregate)
  private final Map<String, Map<UUID, ConcurrentSkipListMap<Long, Aggregate>>> byRealm =
      new ConcurrentHashMap<>();

  private InMemoryViewStore() {}

  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder (no configuration today; keeps construction uniform across edd stores). */
  public static final class Builder {
    private Builder() {}

    public InMemoryViewStore build() {
      return new InMemoryViewStore();
    }
  }

  @Override
  public void update(String realm, Aggregate aggregate) {
    if (realm == null) {
      throw new IllegalArgumentException("realm is required");
    }
    if (aggregate == null) {
      throw new IllegalArgumentException("aggregate is required");
    }
    if (aggregate.id() == null) {
      throw new IllegalArgumentException("aggregate id is required");
    }
    if (aggregate.version() <= 0) {
      throw new IllegalArgumentException(
          "aggregate version must be positive, was " + aggregate.version());
    }
    byRealm
        .computeIfAbsent(realm, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(aggregate.id(), k -> new ConcurrentSkipListMap<>())
        .put(aggregate.version(), aggregate); // last-write-wins per version
  }

  @Override
  public <A extends Aggregate> Optional<A> getSnapshot(
      String realm, UUID aggregateId, Class<A> type) {
    Map.Entry<Long, Aggregate> last = history(realm, aggregateId).lastEntry();
    return last == null ? Optional.empty() : Optional.of(type.cast(last.getValue()));
  }

  @Override
  public <A extends Aggregate> Optional<A> getSnapshot(
      String realm, UUID aggregateId, long version, Class<A> type) {
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive, was " + version);
    }
    return Optional.ofNullable(type.cast(history(realm, aggregateId).get(version)));
  }

  private NavigableMap<Long, Aggregate> history(String realm, UUID aggregateId) {
    if (realm == null) {
      throw new IllegalArgumentException("realm is required");
    }
    if (aggregateId == null) {
      throw new IllegalArgumentException("aggregate id is required");
    }
    Map<UUID, ConcurrentSkipListMap<Long, Aggregate>> aggregates = byRealm.get(realm);
    NavigableMap<Long, Aggregate> history = aggregates == null ? null : aggregates.get(aggregateId);
    return history == null ? Collections.emptyNavigableMap() : history;
  }
}
