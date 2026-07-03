package com.alphaprosoft.edd.core;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The concurrent id ⇄ type registration shared by {@code EventId}, {@code CommandId} and {@code
 * QueryId}: at most one instance per id (created atomically), at most one id per type (aliases
 * rejected), mirrored into {@link TypeRegistry} for the storage codec. Re-registration
 * type-consistency checks stay with the caller — each id class knows which of its types must match.
 */
public final class IdRegistry<I> {

  private final String kind;
  private final Map<String, I> byId = new ConcurrentHashMap<>();
  private final Map<Class<?>, I> byType = new ConcurrentHashMap<>();

  public IdRegistry(String kind) {
    this.kind = kind;
  }

  /** The existing instance for {@code id}, or the one {@code create} supplies, registered. */
  public I register(String id, Class<?> type, Supplier<I> create) {
    return byId.computeIfAbsent(
        id,
        key -> {
          I created = create.get();
          I other = byType.putIfAbsent(type, created);
          if (other != null) {
            throw new IllegalStateException(type.getName() + " already registered as " + other);
          }
          try {
            TypeRegistry.register(kind, key, type);
          } catch (RuntimeException e) {
            byType.remove(type, created);
            throw e;
          }
          return created;
        });
  }

  public I byId(String id) {
    return byId.get(id);
  }

  public I byType(Class<?> type) {
    return byType.get(type);
  }

  public Collection<I> values() {
    return byId.values();
  }
}
