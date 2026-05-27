package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.CommandSpec;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventFxHandler;
import com.alphaprosoft.edd.command.EventHandler;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.command.Identity;
import com.alphaprosoft.edd.command.Rejection;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.query.Dep;
import com.alphaprosoft.edd.query.DepBinding;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryHandler;
import com.alphaprosoft.edd.query.QueryId;
import com.alphaprosoft.edd.query.QuerySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Application {

  private final String serviceName;
  private final Map<CommandId<?>, CommandSpec<?, ?>> commands;
  private final Map<EventId<?>, RegisteredEvent<?, ?>> events;
  private final Map<EventId<?>, List<EventFxHandler<?>>> eventFx;
  private final Map<QueryId<?, ?>, RegisteredQuery<?, ?>> queries;
  private final Map<Class<?>, Schema<?>> aggregateSchemas;
  private final Map<QueryId<?, ?>, String> remoteQueryOwners;
  private final EventStore eventStore;
  private final ViewStore viewStore;
  private final RemoteServiceClient remoteClient;
  private final Telemetry telemetry;
  private final Tracer tracer;
  private final Metrics metrics;

  private static final RemoteServiceClient NO_REMOTE =
      new RemoteServiceClient() {
        @Override
        public <Q extends Query, R> R query(
            String service, QueryId<Q, R> id, Q query, RequestMeta meta) {
          throw new IllegalStateException(
              "Dependency targets remote service '"
                  + service
                  + "' but no RemoteServiceClient is configured");
        }
      };

  private Application(Builder b) {
    this.serviceName = b.serviceName;
    // Config is loaded lazily, only when a module/store factory needs it.
    Config config =
        (b.eventStoreFactory != null || b.viewStoreFactory != null || !b.moduleFactories.isEmpty())
            ? (b.config != null ? b.config : Config.load())
            : null;
    // Deferred, app-aware modules register into the (still-mutable) builder maps before they
    // freeze.
    // The factory sees this with serviceName() set; it must not read registrations or stores yet.
    for (ModuleFactory factory : b.moduleFactories) {
      factory.create(this, config).applyTo(b);
    }
    this.commands = Map.copyOf(b.commands);
    this.events = Map.copyOf(b.events);
    this.eventFx = Map.copyOf(b.eventFx);
    this.queries = Map.copyOf(b.queries);
    this.aggregateSchemas = Map.copyOf(b.aggregateSchemas);
    this.remoteQueryOwners = Map.copyOf(b.remoteQueryOwners);
    this.remoteClient = b.remoteClient;
    this.telemetry = b.telemetry;
    this.tracer = b.tracer;
    this.metrics = b.metrics;
    // Stores are resolved last: a factory receives this (now identity- and registration-complete)
    // plus the config, so it can derive its service from serviceName(). Factories must not read the
    // store fields being assigned here.
    this.eventStore =
        b.eventStoreFactory != null ? b.eventStoreFactory.create(this, config) : b.eventStore;
    this.viewStore =
        b.viewStoreFactory != null ? b.viewStoreFactory.create(this, config) : b.viewStore;
    validateLocalDeps();
  }

  public EventStore eventStore() {
    return eventStore;
  }

  public ViewStore viewStore() {
    return viewStore;
  }

  public static Builder builder(String serviceName) {
    return new Builder(serviceName);
  }

  public String serviceName() {
    return serviceName;
  }

  /** Dispatch a single command — one request, one transaction (a batch of one). */
  public <C extends Command> CommandResponse dispatch(C cmd, RequestMeta meta) {
    return dispatchBatch(List.of(cmd), List.of(meta)).getFirst();
  }

  /**
   * Dispatch a request carrying several commands as <b>one transaction</b> (edd-core {@code
   * handle-commands}): they run in order over a shared request cache — so a later command sees an
   * earlier command's in-memory aggregate state — and all their events + identities are persisted
   * atomically (all-or-nothing). Each command gets its own breadcrumb child ({@code breadcrumbs +
   * [i]}). Two requests (e.g. two SQS messages) are two independent transactions.
   */
  public List<CommandResponse> dispatch(List<? extends Command> commands, RequestMeta meta) {
    List<RequestMeta> metas = new ArrayList<>(commands.size());
    for (int i = 0; i < commands.size(); i++) {
      List<Integer> child = new ArrayList<>(meta.breadcrumbs());
      child.add(i);
      metas.add(RequestMeta.builder(meta).breadcrumbs(child).build());
    }
    return dispatchBatch(commands, metas);
  }

  public <Q extends Query, R> R query(QueryId<Q, R> id, Q query, RequestMeta meta) {
    Map<String, String> dims = Map.of("service", serviceName, "query", id.id());
    long startNanos = System.nanoTime();
    telemetry.emit("query.received", correlation(meta, "query", id.id()));
    try (Tracer.Span span = tracer.span("edd.query:" + id.id())) {
      try {
        RegisteredQuery<?, ?> raw = queries.get(id);
        if (raw == null) {
          String owner = remoteQueryOwners.get(id);
          if (owner != null) {
            telemetry.emit("query.routed", correlation(meta, "query", id.id()));
            return remoteClient.query(owner, id, query, meta);
          }
          throw new IllegalStateException("No query handler registered for " + id);
        }
        @SuppressWarnings("unchecked")
        RegisteredQuery<Q, R> reg = (RegisteredQuery<Q, R>) raw;
        R result = runQuery(reg, query, meta);
        telemetry.emit("query.completed", correlation(meta, "query", id.id()));
        return result;
      } catch (RuntimeException e) {
        span.error(e);
        throw e;
      } finally {
        metrics.duration("edd.query.ms", (System.nanoTime() - startNanos) / 1_000_000, dims);
      }
    }
  }

  private record Pending(CommandResponse.Success success, CommandResponse.Failure failure) {
    static Pending ok(CommandResponse.Success s) {
      return new Pending(s, null);
    }

    static Pending fail(CommandResponse.Failure f) {
      return new Pending(null, f);
    }
  }

  private List<CommandResponse> dispatchBatch(
      List<? extends Command> commands, List<RequestMeta> metas) {
    Map<String, String> dims = Map.of("service", serviceName);
    long startNanos = System.nanoTime();
    try (Tracer.Span span = tracer.span("edd.dispatch")) {
      try {
        List<CommandResponse> out = dispatchBatch0(commands, metas);
        metrics.count("edd.command", commands.size(), dims);
        return out;
      } catch (RuntimeException e) {
        span.error(e);
        metrics.count("edd.command.error", 1, dims);
        throw e;
      } finally {
        metrics.duration("edd.dispatch.ms", (System.nanoTime() - startNanos) / 1_000_000, dims);
      }
    }
  }

  /**
   * The transactional engine: process every command over a shared cache, then commit atomically.
   */
  private List<CommandResponse> dispatchBatch0(
      List<? extends Command> commands, List<RequestMeta> metas) {
    if (eventStore == null) {
      throw new IllegalStateException(
          "No event store registered; call Application.builder(...).eventStore(...)");
    }
    String realm = metas.getFirst().realm();

    RequestMeta first = metas.getFirst();
    if (eventStore.findResponse(realm, first.requestId(), first.breadcrumbs()).isPresent()) {
      List<CommandResponse> replay = new ArrayList<>(commands.size());
      for (RequestMeta m : metas) {
        telemetry.emit("command.deduplicated", correlation(m));
        replay.add(
            eventStore
                .findResponse(realm, m.requestId(), m.breadcrumbs())
                .orElseThrow(
                    () -> new IllegalStateException("Partial dedup state for " + m.requestId())));
      }
      return replay;
    }

    RequestCache cache = new RequestCache();
    List<StoredEvent> pendingEvents = new ArrayList<>();
    List<Identity> pendingIdentities = new ArrayList<>();
    List<CommandResponse.Success> successes = new ArrayList<>(commands.size());
    Set<UUID> touched = new LinkedHashSet<>();

    for (int i = 0; i < commands.size(); i++) {
      Command cmd = commands.get(i);
      RequestMeta meta = metas.get(i);
      CommandSpec<?, ?> spec = specFor(cmd);
      telemetry.emit("command.received", correlation(meta, "command", spec.commandId().id()));
      eventStore.logRequest(realm, meta.requestId(), meta.breadcrumbs(), cmd);

      Pending p = processCommand(spec, cmd, meta, cache, pendingEvents, pendingIdentities, touched);
      if (p.failure() != null) {
        // all-or-nothing: nothing has been persisted yet, so simply abort the whole request
        return abort(metas, i, p.failure());
      }
      successes.add(p.success());
    }

    try {
      eventStore.appendBatch(realm, serviceName, pendingEvents, pendingIdentities);
    } catch (OptimisticLockException e) {
      // retryable, deliberately NOT logged as the final response
      for (RequestMeta m : metas) {
        telemetry.emit("command.conflict", correlation(m));
      }
      return repeat(
          metas.size(),
          new CommandResponse.Failure(
              "concurrent-modification", Map.of("aggregateId", e.aggregateId().toString())));
    } catch (IdentityConflictException e) {
      CommandResponse.Failure f =
          new CommandResponse.Failure("identity-conflict", Map.of("name", e.name()));
      for (RequestMeta m : metas) {
        logged(realm, m, m.breadcrumbs(), f);
      }
      return repeat(metas.size(), f);
    }

    if (viewStore != null) {
      for (UUID id : touched) {
        Aggregate agg = cache.aggregate(realm, id);
        if (agg != null) {
          viewStore.update(realm, agg);
        }
      }
    }

    List<CommandResponse> out = new ArrayList<>(successes.size());
    for (int i = 0; i < successes.size(); i++) {
      out.add(logged(realm, metas.get(i), metas.get(i).breadcrumbs(), successes.get(i)));
    }
    return out;
  }

  /**
   * Process one command against the shared cache; accumulate its events/identities (not yet
   * committed).
   */
  private <C extends Command, A extends Aggregate> Pending processCommand(
      CommandSpec<C, A> spec,
      Command rawCmd,
      RequestMeta meta,
      RequestCache cache,
      List<StoredEvent> pendingEvents,
      List<Identity> pendingIdentities,
      Set<UUID> touched) {
    @SuppressWarnings("unchecked")
    C cmd = (C) rawCmd;
    String realm = meta.realm();

    if (spec.consumes() != null) {
      List<String> violations = spec.consumes().violations(cmd);
      if (!violations.isEmpty()) {
        return Pending.fail(
            new CommandResponse.Failure("invalid-command", Map.of("violations", violations)));
      }
    }

    Map<String, Object> resolved = new LinkedHashMap<>();
    ContextImpl<A> depCtx = new ContextImpl<>(resolved, meta, null, viewStore);
    resolveDeps(spec.deps(), depCtx, resolved, cmd, meta, cache);

    UUID aggregateId = spec.id() != null ? spec.id().apply(depCtx, cmd) : null;
    if (aggregateId == null) {
      aggregateId = cmd.id();
    }
    UUID finalAggregateId = aggregateId;

    A current = loadAggregate(spec.aggregateType(), realm, finalAggregateId, cache);

    if (cmd.version() != null && current != null && cmd.version() != current.version()) {
      telemetry.emit("command.conflict", correlation(meta, "command", spec.commandId().id()));
      return Pending.fail(
          new CommandResponse.Failure(
              "concurrent-modification",
              Map.of("expected", cmd.version(), "actual", current.version())));
    }

    ContextImpl<A> ctx = new ContextImpl<>(resolved, meta, current, viewStore);
    List<Event> emittedEvents = new ArrayList<>();
    List<Identity> identities = new ArrayList<>();
    List<Rejection> rejections = new ArrayList<>();
    for (CommandEmission emission : spec.newHandler().handle(ctx, cmd)) {
      switch (emission) {
        case null -> {}
        case Event event -> emittedEvents.add(event);
        case Identity identity -> identities.add(identity);
        case Rejection rejection -> rejections.add(rejection);
      }
    }

    if (!rejections.isEmpty()) {
      return Pending.fail(CommandResponse.rejected(rejections));
    }

    A next = applyAll(current, emittedEvents);
    @SuppressWarnings("unchecked")
    Schema<? super A> stateSchema = (Schema<? super A>) aggregateSchemas.get(spec.aggregateType());
    if (stateSchema != null && next != null) {
      List<String> violations = stateSchema.violations(next);
      if (!violations.isEmpty()) {
        return Pending.fail(
            new CommandResponse.Failure("invalid-state", Map.of("violations", violations)));
      }
    }

    List<Identity> stampedIdentities =
        identities.stream()
            .map(i -> i.aggregateId() == null ? i.withAggregateId(finalAggregateId) : i)
            .toList();

    EventMeta eventMeta = EventMeta.from(meta, Instant.now());
    long base =
        cache.hasBaseSeq(realm, finalAggregateId)
            ? cache.baseSeq(realm, finalAggregateId)
            : eventStore.maxEventSeq(realm, finalAggregateId);
    for (int j = 0; j < emittedEvents.size(); j++) {
      pendingEvents.add(
          new StoredEvent(finalAggregateId, base + j + 1, emittedEvents.get(j), eventMeta));
    }
    cache.putBaseSeq(realm, finalAggregateId, base + emittedEvents.size());
    pendingIdentities.addAll(stampedIdentities);
    if (next != null) {
      cache.putAggregate(realm, finalAggregateId, next);
    }
    touched.add(finalAggregateId);

    // effects react to the event with the event's own meta as ctx (meta flows event -> ctx); pure
    // data
    Context fxCtx =
        new ContextImpl<Aggregate>(Map.of(), eventMeta.toRequestMeta(), null, viewStore);
    List<Command> effects = runFx(fxCtx, emittedEvents);
    return Pending.ok(
        new CommandResponse.Success(finalAggregateId, emittedEvents, stampedIdentities, effects));
  }

  private CommandSpec<?, ?> specFor(Command cmd) {
    @SuppressWarnings("unchecked")
    CommandId<? extends Command> cmdId =
        CommandId.forType((Class<? extends Command>) cmd.getClass());
    CommandSpec<?, ?> spec = commands.get(cmdId);
    if (spec == null) {
      throw new IllegalStateException("No command handler registered for " + cmdId);
    }
    return spec;
  }

  @SuppressWarnings("unchecked")
  private <A extends Aggregate> A loadAggregate(
      Class<A> type, String realm, UUID id, RequestCache cache) {
    if (cache.hasAggregate(realm, id)) {
      return (A) cache.aggregate(realm, id);
    }
    return this.<A>replayAggregate(eventStore.load(realm, id));
  }

  /** A whole-batch failure: report the cause and mark the rest aborted (nothing was persisted). */
  private List<CommandResponse> abort(
      List<RequestMeta> metas, int failedIndex, CommandResponse.Failure cause) {
    for (RequestMeta m : metas) {
      telemetry.emit("command.failed", correlation(m, "code", cause.code()));
    }
    List<CommandResponse> out = new ArrayList<>(metas.size());
    CommandResponse.Failure aborted =
        new CommandResponse.Failure("batch-aborted", Map.of("cause", cause.code()));
    for (int i = 0; i < metas.size(); i++) {
      out.add(i == failedIndex ? cause : aborted);
    }
    return out;
  }

  private List<CommandResponse> repeat(int n, CommandResponse response) {
    List<CommandResponse> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      out.add(response);
    }
    return out;
  }

  private CommandResponse logged(
      String realm, RequestMeta meta, List<Integer> breadcrumbs, CommandResponse resp) {
    eventStore.logResponse(realm, meta.requestId(), breadcrumbs, resp);
    switch (resp) {
      case CommandResponse.Success s -> {
        Map<String, Object> fields = correlation(meta, "events", s.events().size());
        fields.put("effects", s.effects().size());
        telemetry.emit("command.succeeded", fields);
      }
      case CommandResponse.Failure f ->
          telemetry.emit("command.failed", correlation(meta, "code", f.code()));
    }
    return resp;
  }

  private Map<String, Object> correlation(RequestMeta meta, String key, Object value) {
    Map<String, Object> fields = correlation(meta);
    fields.put(key, value);
    return fields;
  }

  private Map<String, Object> correlation(RequestMeta meta) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("service", serviceName);
    fields.put("requestId", meta.requestId().toString());
    fields.put("interactionId", meta.interactionId().toString());
    fields.put("realm", meta.realm());
    fields.put("user", meta.user().id());
    fields.put("breadcrumbs", meta.breadcrumbs().toString());
    return fields;
  }

  private <A extends Aggregate> A replayAggregate(List<StoredEvent> stored) {
    return this.<A>applyAll(null, stored.stream().map(StoredEvent::event).toList());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private <A extends Aggregate> A applyAll(A aggregate, List<? extends Event> events) {
    A result = aggregate;
    for (Event event : events) {
      EventId<?> eid = EventId.forType((Class) event.getClass());
      RegisteredEvent<?, ?> re = this.events.get(eid);
      if (re != null) {
        result = (A) ((EventHandler) re.handler()).apply(result, event);
      }
    }
    long version = (aggregate == null ? 0 : aggregate.version()) + events.size();
    return VersionStamp.withVersion(result, version);
  }

  /**
   * The single deps resolver — used by command deps, query deps, and (recursively) remote queries.
   * Resolves each dep locally by query type, or remotely via {@link RemoteServiceClient} when the
   * dep names another service. Results land in {@code into} so later deps can read earlier ones.
   */
  private <I> void resolveDeps(
      List<DepBinding<I, ?, ?>> deps,
      Context ctx,
      Map<String, Object> into,
      I input,
      RequestMeta meta,
      RequestCache cache) {
    for (DepBinding<I, ?, ?> binding : deps) {
      into.put(binding.key().name(), resolveDep(binding, ctx, input, meta, cache));
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object resolveDep(
      DepBinding<?, ?, ?> binding,
      Context ctx,
      Object input,
      RequestMeta meta,
      RequestCache cache) {
    Dep<?, ?> dep = binding.key();
    Query q = (Query) ((BiFunction) binding.queryFn()).apply(ctx, input);
    RequestCache.DepKey key = new RequestCache.DepKey(dep.service(), q);
    if (cache.hasDep(key)) {
      return cache.dep(key);
    }
    Object result =
        (dep.service() != null && !dep.service().equals(serviceName))
            ? remoteClient.query(dep.service(), (QueryId) dep.queryId(), q, meta)
            : queryByType(q, meta);
    cache.putDep(key, result);
    return result;
  }

  private List<Command> runFx(Context ctx, List<Event> events) {
    List<Command> all = new ArrayList<>();
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
  private List<Command> invokeFx(EventFxHandler<?> handler, Context ctx, Event event) {
    return ((EventFxHandler) handler).fx(ctx, event);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object queryByType(Query q, RequestMeta meta) {
    QueryId<?, ?> id = QueryId.forType((Class) q.getClass());
    RegisteredQuery<?, ?> reg = queries.get(id);
    if (reg == null) {
      throw new IllegalStateException("No query handler registered for " + id);
    }
    return runQuery((RegisteredQuery) reg, q, meta);
  }

  private <Q extends Query, R> R runQuery(RegisteredQuery<Q, R> reg, Q query, RequestMeta meta) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    Context ctx = new ContextImpl<Aggregate>(resolved, meta, null, viewStore);
    resolveDeps(reg.deps(), ctx, resolved, query, meta, new RequestCache());
    R result = reg.handler().handle(ctx, query);
    if (reg.produces() != null) {
      List<String> violations = reg.produces().violations(result);
      if (!violations.isEmpty()) {
        throw new IllegalStateException(
            "Query " + reg.id() + " produced an invalid result: " + violations);
      }
    }
    return result;
  }

  private void validateLocalDeps() {
    for (CommandSpec<?, ?> spec : commands.values()) {
      for (DepBinding<?, ?, ?> binding : spec.deps()) {
        Dep<?, ?> dep = binding.key();
        if (!dep.isRemote() && !queries.containsKey(dep.queryId())) {
          throw new IllegalStateException(
              "Command "
                  + spec.commandId()
                  + " depends on query "
                  + dep.queryId()
                  + " which has no registered handler");
        }
      }
    }
    for (RegisteredQuery<?, ?> reg : queries.values()) {
      for (DepBinding<?, ?, ?> binding : reg.deps()) {
        Dep<?, ?> dep = binding.key();
        if (!dep.isRemote() && !queries.containsKey(dep.queryId())) {
          throw new IllegalStateException(
              "Query "
                  + reg.id()
                  + " depends on query "
                  + dep.queryId()
                  + " which has no registered handler");
        }
      }
    }
  }

  record RegisteredEvent<E extends Event, A extends Aggregate>(
      EventId<E> id, Class<A> aggregateType, EventHandler<E, A> handler) {}

  record RegisteredQuery<Q extends Query, R>(
      QueryId<Q, R> id,
      QueryHandler<Q, R> handler,
      List<DepBinding<Q, ?, ?>> deps,
      Schema<? super R> produces) {}

  public static final class Builder {

    private final String serviceName;
    private final Map<CommandId<?>, CommandSpec<?, ?>> commands = new LinkedHashMap<>();
    private final Map<EventId<?>, RegisteredEvent<?, ?>> events = new LinkedHashMap<>();
    private final Map<EventId<?>, List<EventFxHandler<?>>> eventFx = new LinkedHashMap<>();
    private final Map<QueryId<?, ?>, RegisteredQuery<?, ?>> queries = new LinkedHashMap<>();
    private final Map<Class<?>, Schema<?>> aggregateSchemas = new LinkedHashMap<>();
    private final Map<QueryId<?, ?>, String> remoteQueryOwners = new LinkedHashMap<>();
    private final List<ModuleFactory> moduleFactories = new ArrayList<>();
    private Config config;
    private EventStore eventStore;
    private ViewStore viewStore;
    private EventStoreFactory eventStoreFactory;
    private ViewStoreFactory viewStoreFactory;
    private RemoteServiceClient remoteClient = NO_REMOTE;
    private Telemetry telemetry = Telemetry.NONE;
    private Tracer tracer = Tracer.NONE;
    private Metrics metrics = Metrics.NONE;

    private Builder(String serviceName) {
      this.serviceName = serviceName;
    }

    public Builder telemetry(Telemetry telemetry) {
      this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
      return this;
    }

    public Builder tracer(Tracer tracer) {
      this.tracer = Objects.requireNonNull(tracer, "tracer");
      return this;
    }

    public Builder metrics(Metrics metrics) {
      this.metrics = Objects.requireNonNull(metrics, "metrics");
      return this;
    }

    /**
     * Configuration handed to store factories at {@link #build()}. Optional — defaults to {@link
     * Config#load()} when a factory is set but no config is given.
     */
    public Builder config(Config config) {
      this.config = config;
      return this;
    }

    public Builder eventStore(EventStore eventStore) {
      this.eventStore = eventStore;
      return this;
    }

    /** Defer event-store construction to {@link #build()}, giving the factory the app + config. */
    public Builder eventStore(EventStoreFactory eventStoreFactory) {
      this.eventStoreFactory = eventStoreFactory;
      return this;
    }

    public Builder viewStore(ViewStore viewStore) {
      this.viewStore = viewStore;
      return this;
    }

    /** Defer view-store construction to {@link #build()}, giving the factory the app + config. */
    public Builder viewStore(ViewStoreFactory viewStoreFactory) {
      this.viewStoreFactory = viewStoreFactory;
      return this;
    }

    public Builder remoteClient(RemoteServiceClient remoteClient) {
      this.remoteClient = remoteClient;
      return this;
    }

    public <C extends Command, A extends Aggregate> Builder regCmd(CommandSpec<C, A> spec) {
      if (commands.putIfAbsent(spec.commandId(), spec) != null) {
        throw new IllegalStateException("Duplicate command registration: " + spec.commandId());
      }
      return this;
    }

    public <E extends Event, A extends Aggregate> Builder regApply(
        EventId<E> id, Class<A> aggregateType, EventHandler<E, A> handler) {
      if (events.putIfAbsent(id, new RegisteredEvent<>(id, aggregateType, handler)) != null) {
        throw new IllegalStateException("Duplicate apply registration: " + id);
      }
      return this;
    }

    public <E extends Event> Builder regFx(EventId<E> id, EventFxHandler<E> handler) {
      eventFx.computeIfAbsent(id, k -> new ArrayList<>()).add(handler);
      return this;
    }

    public <Q extends Query, R> Builder regQuery(QueryId<Q, R> id, QueryHandler<Q, R> handler) {
      return regQuery(new QuerySpec<>(id, handler, List.of()));
    }

    public <Q extends Query, R> Builder regQuery(
        QueryId<Q, R> id, Function<QuerySpec.Init<Q, R>, QuerySpec<Q, R>> configure) {
      return regQuery(configure.apply(QuerySpec.builder(id)));
    }

    public <Q extends Query, R> Builder regQuery(QuerySpec<Q, R> spec) {
      RegisteredQuery<Q, R> reg =
          new RegisteredQuery<>(spec.queryId(), spec.handler(), spec.deps(), spec.produces());
      if (queries.putIfAbsent(spec.queryId(), reg) != null) {
        throw new IllegalStateException("Duplicate query registration: " + spec.queryId());
      }
      return this;
    }

    /** Declare that {@code id} is owned by a remote {@code service}; inbound calls route there. */
    public <Q extends Query, R> Builder regRemoteQuery(QueryId<Q, R> id, String service) {
      remoteQueryOwners.put(id, Objects.requireNonNull(service, "service"));
      return this;
    }

    /** Validate an aggregate's state after events are applied (edd-core aggregate-state schema). */
    <A extends Aggregate> void registerAggregateSchema(
        Class<A> aggregateType, Schema<? super A> schema) {
      aggregateSchemas.put(aggregateType, schema);
    }

    /** Absorb a module built with {@link Module#builder(Class)}. */
    public Builder module(Module<?> module) {
      module.applyTo(this);
      return this;
    }

    /** Absorb a module from a supplier, e.g. {@code .module(OrderModule::register)}. */
    public Builder module(Supplier<Module<?>> module) {
      return module(module.get());
    }

    /**
     * Absorb a module that needs app identity or config, built at {@link #build()} time, e.g.
     * {@code .module(SomeModule::register)} where {@code register(Application, Config)}. The
     * everyday no-arg {@code .module(SomeModule::register)} (a {@link Supplier}) is preferred when
     * the module needs neither.
     */
    public Builder module(ModuleFactory module) {
      moduleFactories.add(module);
      return this;
    }

    public Application build() {
      return new Application(this);
    }
  }
}
