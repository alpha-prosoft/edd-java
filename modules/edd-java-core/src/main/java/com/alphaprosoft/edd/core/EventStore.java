package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Identity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only event log, identity reservations, and request/response logs — isolated by {@code
 * realm} (edd-core's multi-tenant DAL). This is the edd-core compliance contract, expressed
 * Java-style (overloads instead of polymorphic params). Implementations: {@code
 * InMemoryEventStore}, and (planned) Postgres / DynamoDB.
 */
public interface EventStore {

  // ---- events -------------------------------------------------------------

  /** All events for an aggregate, ordered ascending by {@code eventSeq}. Empty if none. */
  List<StoredEvent> load(String realm, UUID aggregateId);

  /** Events with {@code eventSeq > afterSeq} (the "from snapshot/version" read). */
  List<StoredEvent> load(String realm, UUID aggregateId, long afterSeq);

  /** Highest {@code eventSeq} for an aggregate, or {@code 0} if none. */
  long maxEventSeq(String realm, UUID aggregateId);

  /**
   * Append events. {@code expectedVersion} is the event count the caller replayed; a mismatch (or a
   * duplicate {@code (aggregateId, eventSeq)}) is rejected — optimistic locking.
   */
  void append(String realm, UUID aggregateId, long expectedVersion, List<StoredEvent> events);

  /**
   * Atomically persist a whole request's output — events (possibly spanning several aggregates) and
   * identity reservations — all-or-nothing (edd-core {@code store-results}). A duplicate {@code
   * (aggregateId, eventSeq)} throws {@link OptimisticLockException}; an identity already bound to a
   * different aggregate throws {@link IdentityConflictException}. On either, nothing is persisted.
   * Each {@link StoredEvent} carries its own {@code aggregateId}, so the caller need not group by
   * it.
   */
  void appendBatch(
      String realm, String service, List<StoredEvent> events, List<Identity> identities);

  /** All events sharing an {@code interactionId} (cross-aggregate grouping). */
  List<StoredEvent> eventsByInteraction(String realm, UUID interactionId);

  // ---- identities (scoped by realm + service) -----------------------------

  /**
   * A name already bound to a different aggregate (same service) throws {@link
   * IdentityConflictException}.
   */
  void reserveIdentities(String realm, String service, List<Identity> identities);

  Optional<UUID> aggregateIdByIdentity(String realm, String service, String name);

  /** Bulk lookup — only present mappings are returned. */
  Map<String, UUID> aggregateIdByIdentity(String realm, String service, Collection<String> names);

  // ---- request / response logging + idempotency ---------------------------

  void logRequest(String realm, UUID requestId, List<Integer> breadcrumbs, Object body);

  void logResponse(
      String realm, UUID requestId, List<Integer> breadcrumbs, CommandResponse response);

  void logError(String realm, UUID requestId, List<Integer> breadcrumbs, Object error);

  /**
   * A previously stored response for {@code (requestId, breadcrumbs)} — the idempotency/dedup key.
   */
  Optional<CommandResponse> findResponse(String realm, UUID requestId, List<Integer> breadcrumbs);
}
