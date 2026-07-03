package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.IdRegistry;
import com.alphaprosoft.edd.core.TypeRegistry;
import java.util.Collection;
import java.util.Optional;

public final class EventId<E extends Event> {

  private static final IdRegistry<EventId<?>> REGISTRY = new IdRegistry<>(TypeRegistry.EVENT);

  private final String id;
  private final Class<E> type;

  private EventId(String id, Class<E> type) {
    this.id = id;
    this.type = type;
  }

  public static <E extends Event> EventId<E> of(String id, Class<E> type) {
    EventId<?> existing = REGISTRY.register(id, type, () -> new EventId<>(id, type));
    if (!existing.type.equals(type)) {
      throw new IllegalStateException(
          "EventId '" + id + "' already registered with type " + existing.type);
    }
    return new EventId<>(id, type);
  }

  public String id() {
    return id;
  }

  public Class<E> type() {
    return type;
  }

  public static Optional<EventId<?>> lookup(String id) {
    return Optional.ofNullable(REGISTRY.byId(id));
  }

  public static <E extends Event> EventId<E> forType(Class<E> type) {
    EventId<?> found = REGISTRY.byType(type);
    if (found == null) {
      throw new IllegalArgumentException("No EventId registered for " + type.getName());
    }
    return new EventId<>(found.id, type);
  }

  public static Collection<EventId<?>> values() {
    return REGISTRY.values();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof EventId<?> other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "EventId[" + id + "]";
  }
}
