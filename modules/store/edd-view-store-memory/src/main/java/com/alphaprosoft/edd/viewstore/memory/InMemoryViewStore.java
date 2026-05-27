package com.alphaprosoft.edd.viewstore.memory;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.ViewStore;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link ViewStore} implementing the compliance contract: version history,
 * highest-version + exact-version snapshots, realm isolation, and strict validation. Realm-scoped.
 */
public final class InMemoryViewStore implements ViewStore {

  // realm -> aggregateId -> (version -> aggregate)
  private final Map<String, Map<UUID, TreeMap<Long, Aggregate>>> byRealm =
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
  public synchronized void update(String realm, Aggregate aggregate) {
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
        .computeIfAbsent(aggregate.id(), k -> new TreeMap<>())
        .put(aggregate.version(), aggregate); // last-write-wins per version
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <A extends Aggregate> Optional<A> getSnapshot(
      String realm, UUID aggregateId) {
    TreeMap<Long, Aggregate> history = history(realm, aggregateId);
    return history.isEmpty() ? Optional.empty() : Optional.of((A) history.lastEntry().getValue());
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <A extends Aggregate> Optional<A> getSnapshot(
      String realm, UUID aggregateId, long version) {
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive, was " + version);
    }
    return Optional.ofNullable((A) history(realm, aggregateId).get(version));
  }

  private TreeMap<Long, Aggregate> history(String realm, UUID aggregateId) {
    if (realm == null) {
      throw new IllegalArgumentException("realm is required");
    }
    if (aggregateId == null) {
      throw new IllegalArgumentException("aggregate id is required");
    }
    return byRealm.getOrDefault(realm, Map.of()).getOrDefault(aggregateId, new TreeMap<>());
  }
}
