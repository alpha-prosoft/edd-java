package com.alphaprosoft.edd;

import java.util.function.Function;

public final class Module<A extends Aggregate> {

    private final Application.Builder app;
    private final Class<A> aggregateType;

    Module(Application.Builder app, Class<A> aggregateType) {
        this.app = app;
        this.aggregateType = aggregateType;
    }

    public Class<A> aggregateType() {
        return aggregateType;
    }

    public <C extends Command> Module<A> regCmd(
            CommandId<C> id, Function<CommandSpec.Init<C, A>, CommandSpec<C, A>> configure) {
        CommandSpec<C, A> spec = configure.apply(CommandSpec.builder(id, aggregateType));
        app.regCmd(spec);
        return this;
    }

    public <E extends Event> Module<A> regApply(EventId<E> id, EventHandler<E, A> handler) {
        app.regApply(id, aggregateType, handler);
        return this;
    }

    public <E extends Event> Module<A> regFx(EventId<E> id, EventFxHandler<E> handler) {
        app.regFx(id, handler);
        return this;
    }

    public <Q extends Query, R> Module<A> regQuery(QueryId<Q, R> id, QueryHandler<Q, R> handler) {
        app.regQuery(id, handler);
        return this;
    }
}
