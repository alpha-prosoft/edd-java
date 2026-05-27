package com.alphaprosoft.edd.core;

import java.util.UUID;

/**
 * Thrown by {@link EventStore#appendBatch} when an event's {@code (aggregateId, eventSeq)} already
 * exists — a concurrent writer advanced the aggregate since it was replayed. The whole batch is
 * rejected (nothing persisted); the dispatch surfaces it as a retryable {@code
 * concurrent-modification}.
 */
public final class OptimisticLockException extends RuntimeException {

  private final transient UUID aggregateId;

  public OptimisticLockException(UUID aggregateId, long eventSeq) {
    super(
        "Concurrent modification of "
            + aggregateId
            + ": event-seq "
            + eventSeq
            + " already exists");
    this.aggregateId = aggregateId;
  }

  public UUID aggregateId() {
    return aggregateId;
  }
}
