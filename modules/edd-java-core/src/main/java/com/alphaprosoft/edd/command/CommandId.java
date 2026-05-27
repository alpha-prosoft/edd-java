package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.TypeRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandId<C extends Command> {

  private static final Map<String, CommandId<?>> BY_ID = new ConcurrentHashMap<>();
  private static final Map<Class<?>, CommandId<?>> BY_TYPE = new ConcurrentHashMap<>();

  private final String id;
  private final Class<C> type;

  private CommandId(String id, Class<C> type) {
    this.id = id;
    this.type = type;
  }

  public static <C extends Command> CommandId<C> of(String id, Class<C> type) {
    CommandId<?> existing = BY_ID.get(id);
    if (existing != null) {
      if (!existing.type.equals(type)) {
        throw new IllegalStateException(
            "CommandId '" + id + "' already registered with type " + existing.type);
      }
      @SuppressWarnings("unchecked")
      CommandId<C> cast = (CommandId<C>) existing;
      return cast;
    }
    CommandId<C> created = new CommandId<>(id, type);
    BY_ID.put(id, created);
    BY_TYPE.put(type, created);
    TypeRegistry.register(TypeRegistry.COMMAND, id, type);
    return created;
  }

  public String id() {
    return id;
  }

  public Class<C> type() {
    return type;
  }

  public static Optional<CommandId<?>> lookup(String id) {
    return Optional.ofNullable(BY_ID.get(id));
  }

  public static <C extends Command> CommandId<C> forType(Class<C> type) {
    CommandId<?> found = BY_TYPE.get(type);
    if (found == null) {
      throw new IllegalArgumentException("No CommandId registered for " + type.getName());
    }
    @SuppressWarnings("unchecked")
    CommandId<C> cast = (CommandId<C>) found;
    return cast;
  }

  public static Collection<CommandId<?>> values() {
    return BY_ID.values();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CommandId<?> other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "CommandId[" + id + "]";
  }
}
