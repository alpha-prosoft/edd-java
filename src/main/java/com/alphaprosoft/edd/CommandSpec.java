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

    private CommandSpec(Builder<C, A> b) {
        if (b.handler == null) {
            throw new IllegalStateException("CommandSpec missing handler for " + b.id);
        }
        this.id = b.id;
        this.aggregateType = b.aggregateType;
        this.handler = b.handler;
        this.deps = b.deps != null ? b.deps : Deps.empty();
        this.idFn = b.idFn;
    }

    public static <C extends Command, A extends Aggregate> Builder<C, A> builder(
            CommandId<C> id, Class<A> aggregateType) {
        return new Builder<>(id, aggregateType);
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

    public static final class Builder<C extends Command, A extends Aggregate> {

        private final CommandId<C> id;
        private final Class<A> aggregateType;
        private CommandHandler<C, A> handler;
        private Deps<C> deps;
        private BiFunction<Context, C, UUID> idFn;

        private Builder(CommandId<C> id, Class<A> aggregateType) {
            this.id = id;
            this.aggregateType = aggregateType;
        }

        public Builder<C, A> handler(CommandHandler<C, A> handler) {
            this.handler = handler;
            return this;
        }

        public Builder<C, A> idFn(BiFunction<Context, C, UUID> idFn) {
            this.idFn = idFn;
            return this;
        }

        public Builder<C, A> deps(Deps<C> deps) {
            this.deps = deps;
            return this;
        }

        public CommandSpec<C, A> build() {
            return new CommandSpec<>(this);
        }
    }
}
