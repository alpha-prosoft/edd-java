package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.TypeRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class EventId<E extends Event> {

  private static final Map<String, EventId<?>> BY_ID = new ConcurrentHashMap<>();
  private static final Map<Class<?>, EventId<?>> BY_TYPE = new ConcurrentHashMap<>();

  private final String id;
  private final Class<E> type;

  private EventId(String id, Class<E> type) {
    this.id = id;
    this.type = type;
  }

  public static <E extends Event> EventId<E> of(String id, Class<E> type) {
    EventId<?> existing = BY_ID.get(id);
    if (existing != null) {
      if (!existing.type.equals(type)) {
        throw new IllegalStateException(
            "EventId '" + id + "' already registered with type " + existing.type);
      }
      @SuppressWarnings("unchecked")
      EventId<E> cast = (EventId<E>) existing;
      return cast;
    }
    EventId<E> created = new EventId<>(id, type);
    BY_ID.put(id, created);
    BY_TYPE.put(type, created);
    TypeRegistry.register(TypeRegistry.EVENT, id, type);
    return created;
  }

  public String id() {
    return id;
  }

  public Class<E> type() {
    return type;
  }

  public static Optional<EventId<?>> lookup(String id) {
    return Optional.ofNullable(BY_ID.get(id));
  }

  public static <E extends Event> EventId<E> forType(Class<E> type) {
    EventId<?> found = BY_TYPE.get(type);
    if (found == null) {
      throw new IllegalArgumentException("No EventId registered for " + type.getName());
    }
    @SuppressWarnings("unchecked")
    EventId<E> cast = (EventId<E>) found;
    return cast;
  }

  public static Collection<EventId<?>> values() {
    return BY_ID.values();
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
