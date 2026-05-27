package com.alphaprosoft.edd.eventstore.memory;

import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Identity;
import com.alphaprosoft.edd.core.EventMeta;
import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.IdentityConflictException;
import com.alphaprosoft.edd.core.OptimisticLockException;
import com.alphaprosoft.edd.core.StoredEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link EventStore} implementing the full compliance contract. Realm-scoped.
 */
public final class InMemoryEventStore implements EventStore {

  private record IdentityKey(String service, String name) {}

  private record LogKey(UUID requestId, List<Integer> breadcrumbs) {}

  private final Map<String, Map<UUID, List<StoredEvent>>> events = new ConcurrentHashMap<>();
  private final Map<String, Map<IdentityKey, UUID>> identities = new ConcurrentHashMap<>();
  private final Map<String, Map<LogKey, CommandResponse>> responses = new ConcurrentHashMap<>();
  private final Map<String, Map<LogKey, Object>> requests = new ConcurrentHashMap<>();
  private final Map<String, Map<LogKey, Object>> errors = new ConcurrentHashMap<>();

  private InMemoryEventStore() {}

  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder (no configuration today; keeps construction uniform across edd stores). */
  public static final class Builder {
    private Builder() {}

    public InMemoryEventStore build() {
      return new InMemoryEventStore();
    }
  }

  @Override
  public synchronized List<StoredEvent> load(String realm, UUID aggregateId) {
    return List.copyOf(aggregate(realm, aggregateId));
  }

  @Override
  public synchronized List<StoredEvent> load(String realm, UUID aggregateId, long afterSeq) {
    return aggregate(realm, aggregateId).stream().filter(e -> e.eventSeq() > afterSeq).toList();
  }

  @Override
  public synchronized long maxEventSeq(String realm, UUID aggregateId) {
    return aggregate(realm, aggregateId).stream().mapToLong(StoredEvent::eventSeq).max().orElse(0L);
  }

  @Override
  public synchronized void append(
      String realm, UUID aggregateId, long expectedVersion, List<StoredEvent> toStore) {
    List<StoredEvent> current =
        events
            .computeIfAbsent(realm, k -> new HashMap<>())
            .computeIfAbsent(aggregateId, k -> new ArrayList<>());
    if (current.size() != expectedVersion) {
      throw new IllegalStateException(
          "Concurrent modification of "
              + aggregateId
              + ": expected "
              + expectedVersion
              + " events but found "
              + current.size());
    }
    for (StoredEvent se : toStore) {
      for (StoredEvent existing : current) {
        if (existing.eventSeq() == se.eventSeq()) {
          throw new IllegalStateException(
              "Duplicate event-seq " + se.eventSeq() + " for " + aggregateId);
        }
      }
      current.add(se);
    }
  }

  @Override
  public synchronized void appendBatch(
      String realm, String service, List<StoredEvent> toStore, List<Identity> toReserve) {
    Map<UUID, List<StoredEvent>> byAggregate = events.computeIfAbsent(realm, k -> new HashMap<>());
    // validate everything before mutating anything — all-or-nothing
    for (StoredEvent se : toStore) {
      for (StoredEvent existing : byAggregate.getOrDefault(se.aggregateId(), List.of())) {
        if (existing.eventSeq() == se.eventSeq()) {
          throw new OptimisticLockException(se.aggregateId(), se.eventSeq());
        }
      }
    }
    Map<IdentityKey, UUID> reserved = identities.computeIfAbsent(realm, k -> new HashMap<>());
    for (Identity identity : toReserve) {
      UUID existing = reserved.get(new IdentityKey(service, identity.name()));
      if (existing != null && !existing.equals(identity.aggregateId())) {
        throw new IdentityConflictException(identity.name(), existing, identity.aggregateId());
      }
    }
    for (StoredEvent se : toStore) {
      byAggregate.computeIfAbsent(se.aggregateId(), k -> new ArrayList<>()).add(se);
    }
    for (Identity identity : toReserve) {
      reserved.put(new IdentityKey(service, identity.name()), identity.aggregateId());
    }
  }

  @Override
  public synchronized List<StoredEvent> eventsByInteraction(String realm, UUID interactionId) {
    List<StoredEvent> out = new ArrayList<>();
    for (List<StoredEvent> perAggregate : events.getOrDefault(realm, Map.of()).values()) {
      for (StoredEvent se : perAggregate) {
        if (interactionId.toString().equals(se.meta().get(EventMeta.INTERACTION_ID))) {
          out.add(se);
        }
      }
    }
    return out;
  }

  @Override
  public synchronized void reserveIdentities(
      String realm, String service, List<Identity> toReserve) {
    Map<IdentityKey, UUID> reserved = identities.computeIfAbsent(realm, k -> new HashMap<>());
    for (Identity identity : toReserve) {
      IdentityKey key = new IdentityKey(service, identity.name());
      UUID existing = reserved.get(key);
      if (existing != null && !existing.equals(identity.aggregateId())) {
        throw new IdentityConflictException(identity.name(), existing, identity.aggregateId());
      }
    }
    for (Identity identity : toReserve) {
      reserved.put(new IdentityKey(service, identity.name()), identity.aggregateId());
    }
  }

  @Override
  public synchronized Optional<UUID> aggregateIdByIdentity(
      String realm, String service, String name) {
    return Optional.ofNullable(
        identities.getOrDefault(realm, Map.of()).get(new IdentityKey(service, name)));
  }

  @Override
  public synchronized Map<String, UUID> aggregateIdByIdentity(
      String realm, String service, Collection<String> names) {
    Map<IdentityKey, UUID> reserved = identities.getOrDefault(realm, Map.of());
    Map<String, UUID> out = new LinkedHashMap<>();
    for (String name : names) {
      UUID id = reserved.get(new IdentityKey(service, name));
      if (id != null) {
        out.put(name, id);
      }
    }
    return out;
  }

  @Override
  public synchronized void logRequest(
      String realm, UUID requestId, List<Integer> breadcrumbs, Object body) {
    requests
        .computeIfAbsent(realm, k -> new HashMap<>())
        .put(new LogKey(requestId, breadcrumbs), body);
  }

  @Override
  public synchronized void logResponse(
      String realm, UUID requestId, List<Integer> breadcrumbs, CommandResponse response) {
    responses
        .computeIfAbsent(realm, k -> new HashMap<>())
        .put(new LogKey(requestId, breadcrumbs), response);
  }

  @Override
  public synchronized void logError(
      String realm, UUID requestId, List<Integer> breadcrumbs, Object error) {
    errors
        .computeIfAbsent(realm, k -> new HashMap<>())
        .put(new LogKey(requestId, breadcrumbs), error);
  }

  @Override
  public synchronized Optional<CommandResponse> findResponse(
      String realm, UUID requestId, List<Integer> breadcrumbs) {
    return Optional.ofNullable(
        responses.getOrDefault(realm, Map.of()).get(new LogKey(requestId, breadcrumbs)));
  }

  private List<StoredEvent> aggregate(String realm, UUID aggregateId) {
    return events.getOrDefault(realm, Map.of()).getOrDefault(aggregateId, List.of());
  }
}
