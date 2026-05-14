package com.alphaprosoft.edd;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

public final class CommandSpec<C extends Command, A extends Aggregate> {

    private final CommandId<C> id;
    private final Class<A> aggregateType;
    private final CommandHandler<C, A> handler;
    private final Deps<C> deps;
    private final BiFunction<Context, C, UUID> idFn;

    private CommandSpec(
            CommandId<C> id,
            Class<A> aggregateType,
            CommandHandler<C, A> handler,
            Deps<C> deps,
            BiFunction<Context, C, UUID> idFn) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.handler = handler;
        this.deps = deps;
        this.idFn = idFn;
    }

    public static <C extends Command, A extends Aggregate> Init<C, A> builder(CommandId<C> id, Class<A> aggregateType) {
        return new StagedBuilder<>(id, aggregateType);
    }

    public CommandId<C> id() {
        return id;
    }

    public Class<A> aggregateType() {
        return aggregateType;
    }

    public CommandHandler<C, A> handler() {
        return handler;
    }

    public Deps<C> deps() {
        return deps;
    }

    public Optional<BiFunction<Context, C, UUID>> idFn() {
        return Optional.ofNullable(idFn);
    }

    /** First stage — a handler is required before anything else can be set. */
    public interface Init<C extends Command, A extends Aggregate> {
        Builder<C, A> handler(CommandHandler<C, A> handler);
    }

    /** Second stage — optionals and build. */
    public interface Builder<C extends Command, A extends Aggregate> {
        Builder<C, A> deps(Deps<C> deps);

        Builder<C, A> idFn(BiFunction<Context, C, UUID> idFn);

        CommandSpec<C, A> build();
    }

    private static final class StagedBuilder<C extends Command, A extends Aggregate>
            implements Init<C, A>, Builder<C, A> {

        private final CommandId<C> id;
        private final Class<A> aggregateType;
        private CommandHandler<C, A> handler;
        private Deps<C> deps = Deps.empty();
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
        public Builder<C, A> deps(Deps<C> deps) {
            this.deps = deps;
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
