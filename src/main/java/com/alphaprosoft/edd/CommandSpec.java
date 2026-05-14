package com.alphaprosoft.edd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

public record CommandSpec<C extends Command, A extends Aggregate>(
        CommandId<C> id,
        Class<A> aggregateType,
        CommandHandler<C, A> handler,
        List<DepBinding<C, ?, ?>> deps,
        BiFunction<Context, C, UUID> idFn) {

    public CommandSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(handler, "handler");
        deps = deps == null ? List.of() : List.copyOf(deps);
    }

    public static <C extends Command, A extends Aggregate> Init<C, A> builder(CommandId<C> id, Class<A> aggregateType) {
        return new StagedBuilder<>(id, aggregateType);
    }

    public record DepBinding<C extends Command, Q extends Query, T>(
            Dep<Q, T> key, BiFunction<Context, ? super C, Q> queryFn) {}

    /** First stage — a handler is required before anything else can be set. */
    public sealed interface Init<C extends Command, A extends Aggregate> permits StagedBuilder {
        Builder<C, A> handler(CommandHandler<C, A> handler);
    }

    /**
     * Second stage — after the handler is set, the command type {@code C} is fixed.
     * {@code dep(...)} lambdas see {@code cmd} as the specific command record; no type witness needed.
     */
    public sealed interface Builder<C extends Command, A extends Aggregate> permits StagedBuilder {

        <Q extends Query, T> Builder<C, A> dep(Dep<Q, T> key, BiFunction<Context, ? super C, Q> queryFn);

        Builder<C, A> idFn(BiFunction<Context, C, UUID> idFn);

        CommandSpec<C, A> build();
    }

    private static final class StagedBuilder<C extends Command, A extends Aggregate>
            implements Init<C, A>, Builder<C, A> {

        private final CommandId<C> id;
        private final Class<A> aggregateType;
        private CommandHandler<C, A> handler;
        private final List<DepBinding<C, ?, ?>> deps = new ArrayList<>();
        private BiFunction<Context, C, UUID> idFn;

        StagedBuilder(CommandId<C> id, Class<A> aggregateType) {
            this.id = id;
            this.aggregateType = aggregateType;
        }

        @Override
        public Builder<C, A> handler(CommandHandler<C, A> handler) {
            this.handler = handler;
            return this;
        }

        @Override
        public <Q extends Query, T> Builder<C, A> dep(Dep<Q, T> key, BiFunction<Context, ? super C, Q> queryFn) {
            deps.add(new DepBinding<>(key, queryFn));
            return this;
        }

        @Override
        public Builder<C, A> idFn(BiFunction<Context, C, UUID> idFn) {
            this.idFn = idFn;
            return this;
        }

        @Override
        public CommandSpec<C, A> build() {
            return new CommandSpec<>(id, aggregateType, handler, deps, idFn);
        }
    }
}
