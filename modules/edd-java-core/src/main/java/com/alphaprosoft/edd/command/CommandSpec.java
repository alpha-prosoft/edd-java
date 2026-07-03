package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Context;
import com.alphaprosoft.edd.core.Schema;
import com.alphaprosoft.edd.query.Dep;
import com.alphaprosoft.edd.query.DepBinding;
import com.alphaprosoft.edd.query.Query;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

public record CommandSpec<C extends Command, A extends Aggregate>(
    CommandId<C> commandId,
    Class<A> aggregateType,
    Class<? extends CommandHandler<C, A>> handlerClass,
    List<DepBinding<C, ?, ?>> deps,
    BiFunction<Context, C, UUID> id,
    Schema<? super C> consumes) {

  private static final ClassValue<Constructor<?>> CTOR_CACHE =
      new ClassValue<>() {
        @Override
        protected Constructor<?> computeValue(Class<?> type) {
          try {
            return type.getConstructor();
          } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                type.getName() + " must have a public no-arg constructor", e);
          }
        }
      };

  public CommandSpec {
    Objects.requireNonNull(commandId, "commandId");
    Objects.requireNonNull(aggregateType, "aggregateType");
    Objects.requireNonNull(handlerClass, "handlerClass");
    CTOR_CACHE.get(handlerClass);
    deps = deps == null ? List.of() : List.copyOf(deps);
  }

  /** Create a fresh handler instance for one dispatch. */
  public CommandHandler<C, A> newHandler() {
    try {
      return handlerClass.cast(CTOR_CACHE.get(handlerClass).newInstance());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to instantiate " + handlerClass.getName(), e);
    }
  }

  public static <C extends Command, A extends Aggregate> Init<C, A> builder(
      CommandId<C> commandId, Class<A> aggregateType) {
    return new StagedBuilder<>(commandId, aggregateType);
  }

  /** First stage — a handler is required before anything else can be set. */
  public sealed interface Init<C extends Command, A extends Aggregate> permits StagedBuilder {
    <H extends CommandHandler<C, A>> Builder<C, A> handler(Class<H> handlerClass);
  }

  /**
   * Second stage — after the handler is set, the command type {@code C} is fixed. {@code dep(...)}
   * lambdas see {@code cmd} as the specific command record; no type witness needed.
   */
  public sealed interface Builder<C extends Command, A extends Aggregate> permits StagedBuilder {

    <Q extends Query, T> Builder<C, A> dep(
        Dep<Q, T> key, BiFunction<Context, ? super C, Q> queryFn);

    Builder<C, A> id(BiFunction<Context, C, UUID> id);

    /** Validate the incoming command before the handler runs (edd-core {@code :consumes}). */
    Builder<C, A> consumes(Schema<? super C> consumes);

    CommandSpec<C, A> build();
  }

  private static final class StagedBuilder<C extends Command, A extends Aggregate>
      implements Init<C, A>, Builder<C, A> {

    private final CommandId<C> commandId;
    private final Class<A> aggregateType;
    private Class<? extends CommandHandler<C, A>> handlerClass;
    private final List<DepBinding<C, ?, ?>> deps = new ArrayList<>();
    private BiFunction<Context, C, UUID> id;
    private Schema<? super C> consumes;

    StagedBuilder(CommandId<C> commandId, Class<A> aggregateType) {
      this.commandId = commandId;
      this.aggregateType = aggregateType;
    }

    @Override
    public <H extends CommandHandler<C, A>> Builder<C, A> handler(Class<H> handlerClass) {
      this.handlerClass = handlerClass;
      return this;
    }

    @Override
    public <Q extends Query, T> Builder<C, A> dep(
        Dep<Q, T> key, BiFunction<Context, ? super C, Q> queryFn) {
      deps.add(new DepBinding<>(key, queryFn));
      return this;
    }

    @Override
    public Builder<C, A> id(BiFunction<Context, C, UUID> id) {
      this.id = id;
      return this;
    }

    @Override
    public Builder<C, A> consumes(Schema<? super C> consumes) {
      this.consumes = consumes;
      return this;
    }

    @Override
    public CommandSpec<C, A> build() {
      return new CommandSpec<>(commandId, aggregateType, handlerClass, deps, id, consumes);
    }
  }
}
