package com.alphaprosoft.edd.core;

import java.util.Optional;
import java.util.UUID;

/**
 * The read side: current aggregate state projected from events, with version history, isolated by
 * {@code realm}. edd-core compliance contract, Java-style: {@link #getSnapshot(String, UUID)} for
 * the latest version and an overload for an exact version (rather than a polymorphic id-or-query
 * param). Implementations: {@code InMemoryViewStore}, and (planned) Postgres / S3.
 *
 * <p>{@link #update} must reject invalid aggregates (null, missing/non-UUID id,
 * missing/non-positive version) and keep prior versions retrievable.
 */
public interface ViewStore {

  /**
   * Store the aggregate as the current state and record its version in history (last-write-wins per
   * version).
   */
  void update(String realm, Aggregate aggregate);

  /** Highest-version snapshot for an id, or empty if none. */
  <A extends Aggregate> Optional<A> getSnapshot(String realm, UUID aggregateId);

  /** Exact-version snapshot, or empty if that version was never stored. */
  <A extends Aggregate> Optional<A> getSnapshot(String realm, UUID aggregateId, long version);
}
