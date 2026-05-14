package com.alphaprosoft.edd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

public final class Application {

    private final String serviceName;
    private final Map<CommandId<?>, CommandSpec<?, ?>> commands;
    private final Map<EventId<?>, RegisteredEvent<?, ?>> events;
    private final Map<EventId<?>, List<EventFxHandler<?>>> eventFx;
    private final Map<QueryId<?, ?>, RegisteredQuery<?, ?>> queries;
    private final RemoteResolver remoteResolver;

    private Application(Builder b) {
        this.serviceName = b.serviceName;
        this.commands = Map.copyOf(b.commands);
        this.events = Map.copyOf(b.events);
        this.eventFx = Map.copyOf(b.eventFx);
        this.queries = Map.copyOf(b.queries);
        this.remoteResolver = b.remoteResolver;
        validateLocalDeps();
    }

    public static Builder builder(String serviceName) {
        return new Builder(serviceName);
    }

    public String serviceName() {
        return serviceName;
    }

    public <C extends Command> CommandResponse dispatch(C cmd, RequestMeta meta) {
        @SuppressWarnings("unchecked")
        Class<C> cmdClass = (Class<C>) cmd.getClass();
        CommandId<C> cmdId = CommandId.forType(cmdClass);
        CommandSpec<?, ?> raw = commands.get(cmdId);
        if (raw == null) {
            throw new IllegalStateException("No command handler registered for " + cmdId);
        }
        @SuppressWarnings("unchecked")
        CommandSpec<C, ?> spec = (CommandSpec<C, ?>) raw;
        return dispatchTyped(spec, cmd, meta);
    }

    public <Q extends Query, R> R query(QueryId<Q, R> id, Q query, RequestMeta meta) {
        RegisteredQuery<?, ?> raw = queries.get(id);
        if (raw == null) {
            throw new IllegalStateException("No query handler registered for " + id);
        }
        @SuppressWarnings("unchecked")
        RegisteredQuery<Q, R> reg = (RegisteredQuery<Q, R>) raw;
        Context ctx = new ContextImpl(new HashMap<>(), meta.requestId(), meta.interactionId());
        return reg.handler().handle(ctx, query);
    }

    private <C extends Command, A extends Aggregate> CommandResponse dispatchTyped(
            CommandSpec<C, A> spec, C cmd, RequestMeta meta) {

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Deps.Registered<C, ?, ?> reg : spec.deps().all()) {
            Context partial = new ContextImpl(resolved, meta.requestId(), meta.interactionId());
            Object result = resolveDep(reg, partial, cmd, meta);
            resolved.put(reg.key().name(), result);
        }

        Context ctx = new ContextImpl(resolved, meta.requestId(), meta.interactionId());

        UUID resolvedId = spec.idFn().map(fn -> fn.apply(ctx, cmd)).orElse(cmd.id());
        if (resolvedId == null) {
            resolvedId = cmd.id();
        }

        HandlerResult<A> result = spec.handler().handle(ctx, cmd);
        return switch (result) {
            case HandlerResult.Events<A> e -> {
                List<CommandEnvelope<?>> fxOut = runFx(ctx, e.events());
                yield new CommandResponse.Success(e.events(), fxOut);
            }
            case HandlerResult.Error<A> err -> new CommandResponse.Failure(err.code(), err.details());
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveDep(Deps.Registered<?, ?, ?> reg, Context ctx, Object cmd, RequestMeta meta) {
        Query q = (Query) ((BiFunction) reg.queryFn()).apply(ctx, cmd);
        Dep<?, ?> dep = reg.key();
        if (dep.service().isPresent()) {
            return remoteResolver.resolve(dep.service().get(), q);
        }
        return queryByType(q, meta);
    }

    private List<CommandEnvelope<?>> runFx(Context ctx, List<Event> events) {
        List<CommandEnvelope<?>> all = new ArrayList<>();
        for (Event event : events) {
            @SuppressWarnings("unchecked")
            Class<Event> eClass = (Class<Event>) event.getClass();
            EventId<?> eid = EventId.forType(eClass);
            List<EventFxHandler<?>> handlers = eventFx.get(eid);
            if (handlers == null) {
                continue;
            }
            for (EventFxHandler<?> handler : handlers) {
                all.addAll(invokeFx(handler, ctx, event));
            }
        }
        return all;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<CommandEnvelope<?>> invokeFx(EventFxHandler<?> handler, Context ctx, Event event) {
        return ((EventFxHandler) handler).fx(ctx, event);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object queryByType(Query q, RequestMeta meta) {
        QueryId<?, ?> id = QueryId.forType((Class) q.getClass());
        RegisteredQuery<?, ?> reg = queries.get(id);
        if (reg == null) {
            throw new IllegalStateException("No query handler registered for " + id);
        }
        Context ctx = new ContextImpl(new HashMap<>(), meta.requestId(), meta.interactionId());
        return ((QueryHandler) reg.handler()).handle(ctx, q);
    }

    private void validateLocalDeps() {
        for (CommandSpec<?, ?> spec : commands.values()) {
            for (Deps.Registered<?, ?, ?> reg : spec.deps().all()) {
                Dep<?, ?> dep = reg.key();
                if (dep.service().isEmpty() && !queries.containsKey(dep.queryId())) {
                    throw new IllegalStateException("Command " + spec.id() + " depends on local query " + dep.queryId()
                            + " which has no registered handler");
                }
            }
        }
    }

    record RegisteredEvent<E extends Event, A extends Aggregate>(
            EventId<E> id, Class<A> aggregateType, EventHandler<E, A> handler) {}

    record RegisteredQuery<Q extends Query, R>(QueryId<Q, R> id, QueryHandler<Q, R> handler) {}

    public static final class Builder {

        private final String serviceName;
        private final Map<CommandId<?>, CommandSpec<?, ?>> commands = new LinkedHashMap<>();
        private final Map<EventId<?>, RegisteredEvent<?, ?>> events = new LinkedHashMap<>();
        private final Map<EventId<?>, List<EventFxHandler<?>>> eventFx = new LinkedHashMap<>();
        private final Map<QueryId<?, ?>, RegisteredQuery<?, ?>> queries = new LinkedHashMap<>();
        private RemoteResolver remoteResolver = (svc, query) -> {
            throw new UnsupportedOperationException("No RemoteResolver configured (service=" + svc + ")");
        };

        private Builder(String serviceName) {
            this.serviceName = serviceName;
        }

        public <C extends Command, A extends Aggregate> Builder regCmd(CommandSpec<C, A> spec) {
            if (commands.putIfAbsent(spec.id(), spec) != null) {
                throw new IllegalStateException("Duplicate command registration: " + spec.id());
            }
            return this;
        }

        public <E extends Event, A extends Aggregate> Builder regEvent(
                EventId<E> id, Class<A> aggregateType, EventHandler<E, A> handler) {
            if (events.putIfAbsent(id, new RegisteredEvent<>(id, aggregateType, handler)) != null) {
                throw new IllegalStateException("Duplicate event registration: " + id);
            }
            return this;
        }

        public <E extends Event> Builder regEventFx(EventId<E> id, EventFxHandler<E> handler) {
            eventFx.computeIfAbsent(id, k -> new ArrayList<>()).add(handler);
            return this;
        }

        public <Q extends Query, R> Builder regQuery(QueryId<Q, R> id, QueryHandler<Q, R> handler) {
            if (queries.putIfAbsent(id, new RegisteredQuery<>(id, handler)) != null) {
                throw new IllegalStateException("Duplicate query registration: " + id);
            }
            return this;
        }

        public Builder remoteResolver(RemoteResolver resolver) {
            this.remoteResolver = resolver;
            return this;
        }

        public Application build() {
            return new Application(this);
        }
    }
}
