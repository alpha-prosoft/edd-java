package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.IdRegistry;
import com.alphaprosoft.edd.core.TypeRegistry;
import java.util.Collection;
import java.util.Optional;

public final class CommandId<C extends Command> {

  private static final IdRegistry<CommandId<?>> REGISTRY = new IdRegistry<>(TypeRegistry.COMMAND);

  private final String id;
  private final Class<C> type;

  private CommandId(String id, Class<C> type) {
    this.id = id;
    this.type = type;
  }

  public static <C extends Command> CommandId<C> of(String id, Class<C> type) {
    CommandId<?> existing = REGISTRY.register(id, type, () -> new CommandId<>(id, type));
    if (!existing.type.equals(type)) {
      throw new IllegalStateException(
          "CommandId '" + id + "' already registered with type " + existing.type);
    }
    return new CommandId<>(id, type);
  }

  public String id() {
    return id;
  }

  public Class<C> type() {
    return type;
  }

  public static Optional<CommandId<?>> lookup(String id) {
    return Optional.ofNullable(REGISTRY.byId(id));
  }

  public static <C extends Command> CommandId<C> forType(Class<C> type) {
    CommandId<?> found = REGISTRY.byType(type);
    if (found == null) {
      throw new IllegalArgumentException("No CommandId registered for " + type.getName());
    }
    return new CommandId<>(found.id, type);
  }

  public static Collection<CommandId<?>> values() {
    return REGISTRY.values();
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
