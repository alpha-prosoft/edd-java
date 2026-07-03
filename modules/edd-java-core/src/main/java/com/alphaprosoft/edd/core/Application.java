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
import java.util.function.Function;
import java.util.function.Supplier;

public final class Application {

  private final String serviceName;
  private final Map<Class<?>, CommandSpec<?, ?>> commandsByType;
  private final Map<Class<?>, RegisteredEvent<?, ?>> eventsByType;
  private final Map<Class<?>, List<RegisteredFx<?>>> eventFxByType;
  private final Map<QueryId<?, ?>, RegisteredQuery<?, ?>> queries;
  private final Map<Class<?>, RegisteredQuery<?, ?>> queriesByType;
  private final Map<Class<?>, Schema<Object>> aggregateSchemas;
  private final Map<QueryId<?, ?>, String> remoteQueryOwners;
  private final EventStore eventStore;
  private final ViewStore viewStore;
  private final RemoteServiceClient remoteClient;
  private final Telemetry telemetry;
  private final Tracer tracer;
  private final Metrics metrics;
  private final RetryConfig retryConfig;

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
    this.commandsByType = byType(b.commands, CommandId::type);
    this.eventsByType = byType(b.events, EventId::type);
    this.eventFxByType = byFxType(b.eventFx);
    this.queries = Map.copyOf(b.queries);
    this.queriesByType = byType(b.queries, QueryId::queryType);
    this.aggregateSchemas = Map.copyOf(b.aggregateSchemas);
    this.remoteQueryOwners = Map.copyOf(b.remoteQueryOwners);
    this.remoteClient = b.remoteClient;
    this.telemetry = b.telemetry;
    this.tracer = b.tracer;
    this.metrics = b.metrics;
    this.retryConfig = b.retryConfig;
    // Stores are resolved last: a factory receives this (now identity- and registration-complete)
    // plus the config, so it can derive its service from serviceName(). Factories must not read the
    // store fields being assigned here.
    this.eventStore =
        b.eventStoreFactory != null ? b.eventStoreFactory.create(this, config) : b.eventStore;
    this.viewStore =
        b.viewStoreFactory != null ? b.viewStoreFactory.create(this, config) : b.viewStore;
    validateLocalDeps();
  }

  private static <K, V> Map<Class<?>, V> byType(Map<K, V> source, Function<K, Class<?>> type) {
    Map<Class<?>, V> out = new LinkedHashMap<>();
    source.forEach((k, v) -> out.put(type.apply(k), v));
    return Map.copyOf(out);
  }

  private static Map<Class<?>, List<RegisteredFx<?>>> byFxType(
      Map<EventId<?>, List<RegisteredFx<?>>> source) {
    Map<Class<?>, List<RegisteredFx<?>>> out = new LinkedHashMap<>();
    source.forEach((id, handlers) -> out.put(id.type(), List.copyOf(handlers)));
    return Map.copyOf(out);
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

  /**
   * Run a query arriving as an untyped wire payload: {@code decode} receives the id's query type
   * and returns the bound query instance. Shared entry point for the HTTP and Lambda front ends.
   */
  public <Q extends Query, R> R queryDecoded(
      QueryId<Q, R> id, Function<Class<Q>, Q> decode, RequestMeta meta) {
    return query(id, decode.apply(id.queryType()), meta);
  }

  public <Q extends Query, R> R query(QueryId<Q, R> id, Q query, RequestMeta meta) {
    Map<String, String> dims = Map.of("service", serviceName, "query", id.id());
    long startNanos = System.nanoTime();
    telemetry.emit("query.received", correlation(meta, "query", id.id()));
    try (Tracer.Span span = tracer.span("edd.query:" + id.id())) {
      try {
        RegisteredQuery<?, ?> reg = queries.get(id);
        if (reg == null) {
          String owner = remoteQueryOwners.get(id);
          if (owner != null) {
            telemetry.emit("query.routed", correlation(meta, "query", id.id()));
            return remoteClient.query(owner, id, query, meta);
          }
          throw new IllegalStateException("No query handler registered for " + id);
        }
        R result = id.responseType().cast(runQuery(reg, query, meta));
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
   * The transactional engine. Dedup + request logging happen once; the actual processing + commit
   * is retried on {@code concurrent-modification} ({@link OptimisticLockException}) up to the
   * configured attempts. Each retry builds a <b>fresh</b> {@link RequestCache}, so aggregates are
   * re-replayed from the now-updated event store — mirroring edd-core's {@code (retry
   * #(process-commands …) 3)} which clears the request cache between attempts. Identity conflicts
   * and business rejections are not retried; once retries are exhausted the conflict becomes a
   * {@code concurrent-modification} failure.
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

    // Log each request once (not per retry — the log key is stable and must not be duplicated).
    for (int i = 0; i < commands.size(); i++) {
      RequestMeta meta = metas.get(i);
      telemetry.emit(
          "command.received",
          correlation(meta, "command", specFor(commands.get(i)).commandId().id()));
      eventStore.logRequest(realm, meta.requestId(), meta.breadcrumbs(), commands.get(i));
    }

    try {
      return Retry.retry(retryConfig, () -> attemptBatch(commands, metas, realm));
    } catch (OptimisticLockException e) {
      // retries exhausted — report the conflict (deliberately not logged as the final response)
      for (RequestMeta m : metas) {
        telemetry.emit("command.conflict", correlation(m));
      }
      return repeat(
          metas.size(),
          new CommandResponse.Failure(
              "concurrent-modification", Map.of("aggregateId", e.aggregateId().toString())));
    }
  }

  /**
   * One transactional attempt over a fresh cache: process every command, commit events + identities
   * atomically, project to the view store, and log responses. Throws {@link
   * OptimisticLockException} so the caller can retry against fresh state; identity conflicts and
   * rejections return normally (they are not retryable).
   */
  private List<CommandResponse> attemptBatch(
      List<? extends Command> commands, List<RequestMeta> metas, String realm) {
    RequestCache cache = new RequestCache();
    List<StoredEvent> pendingEvents = new ArrayList<>();
    List<Identity> pendingIdentities = new ArrayList<>();
    List<CommandResponse.Success> successes = new ArrayList<>(commands.size());
    Set<UUID> touched = new LinkedHashSet<>();

    for (int i = 0; i < commands.size(); i++) {
      Command cmd = commands.get(i);
      RequestMeta meta = metas.get(i);
      CommandSpec<?, ?> spec = specFor(cmd);
      Pending p = processCommand(spec, cmd, meta, cache, pendingEvents, pendingIdentities, touched);
      if (p.failure() != null) {
        // all-or-nothing: nothing has been persisted yet, so simply abort the whole request
        return abort(realm, metas, i, p.failure());
      }
      successes.add(p.success());
    }

    try {
      eventStore.appendBatch(realm, serviceName, pendingEvents, pendingIdentities);
    } catch (IdentityConflictException e) {
      CommandResponse.Failure f =
          new CommandResponse.Failure("identity-conflict", Map.of("name", e.name()));
      for (RequestMeta m : metas) {
        logged(realm, m, m.breadcrumbs(), f);
      }
      return repeat(metas.size(), f);
    }

    // Only the final committed state per aggregate is projected: versions stepped through inside
    // this transaction (several events or commands on one aggregate) are not stored individually.
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
    C cmd = spec.commandId().type().cast(rawCmd);
    String realm = meta.realm();

    if (spec.consumes() != null) {
      List<String> violations = spec.consumes().violations(cmd);
      if (!violations.isEmpty()) {
        return Pending.fail(
            new CommandResponse.Failure("invalid-command", Map.of("violations", violations)));
      }
    }

    Map<String, Object> resolved = new LinkedHashMap<>();
    ContextImpl<A> depCtx =
        new ContextImpl<>(resolved, meta, null, viewStore, spec.aggregateType());
    resolveDeps(spec.deps(), depCtx, resolved, cmd, meta, cache);

    UUID aggregateId = spec.id() != null ? spec.id().apply(depCtx, cmd) : null;
    if (aggregateId == null) {
      aggregateId = cmd.id();
    }
    UUID finalAggregateId = aggregateId;

    A current = loadAggregate(spec.aggregateType(), realm, finalAggregateId, cache);

    long actualVersion = current == null ? 0 : current.version();
    if (cmd.version() != null && cmd.version() != actualVersion) {
      telemetry.emit("command.conflict", correlation(meta, "command", spec.commandId().id()));
      return Pending.fail(
          new CommandResponse.Failure(
              "concurrent-modification",
              Map.of("expected", cmd.version(), "actual", actualVersion)));
    }

    ContextImpl<A> ctx =
        new ContextImpl<>(resolved, meta, current, viewStore, spec.aggregateType());
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

    A next = applyAll(spec.aggregateType(), current, emittedEvents);
    Schema<Object> stateSchema = aggregateSchemas.get(spec.aggregateType());
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
        new ContextImpl<>(Map.of(), eventMeta.toRequestMeta(), null, viewStore, Aggregate.class);
    List<Command> effects = runFx(fxCtx, emittedEvents);
    return Pending.ok(
        new CommandResponse.Success(finalAggregateId, emittedEvents, stampedIdentities, effects));
  }

  private CommandSpec<?, ?> specFor(Command cmd) {
    CommandSpec<?, ?> spec = commandsByType.get(cmd.getClass());
    if (spec == null) {
      throw new IllegalStateException(
          "No command handler registered for " + cmd.getClass().getName());
    }
    return spec;
  }

  private <A extends Aggregate> A loadAggregate(
      Class<A> type, String realm, UUID id, RequestCache cache) {
    if (cache.hasAggregate(realm, id)) {
      return type.cast(cache.aggregate(realm, id));
    }
    return replayAggregate(type, eventStore.load(realm, id));
  }

  /**
   * A whole-batch failure: log each command's terminal failure response so a redelivery replays it
   * instead of reprocessing, and mark the rest aborted (nothing was persisted). A {@code
   * concurrent-modification} cause is transient and — like the retry-exhaustion path above —
   * deliberately not logged, so a redelivery reprocesses against fresh state.
   */
  private List<CommandResponse> abort(
      String realm, List<RequestMeta> metas, int failedIndex, CommandResponse.Failure cause) {
    CommandResponse.Failure aborted =
        new CommandResponse.Failure("batch-aborted", Map.of("cause", cause.code()));
    boolean retryable = "concurrent-modification".equals(cause.code());
    List<CommandResponse> out = new ArrayList<>(metas.size());
    for (int i = 0; i < metas.size(); i++) {
      RequestMeta m = metas.get(i);
      CommandResponse.Failure resp = i == failedIndex ? cause : aborted;
      if (retryable) {
        telemetry.emit("command.failed", correlation(m, "code", resp.code()));
        out.add(resp);
      } else {
        out.add(logged(realm, m, m.breadcrumbs(), resp));
      }
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

  private <A extends Aggregate> A replayAggregate(Class<A> type, List<StoredEvent> stored) {
    return applyAll(type, null, stored.stream().map(StoredEvent::event).toList());
  }

  private <A extends Aggregate> A applyAll(
      Class<A> type, A aggregate, List<? extends Event> events) {
    A result = aggregate;
    for (Event event : events) {
      RegisteredEvent<?, ?> re = eventsByType.get(event.getClass());
      if (re != null) {
        result = type.cast(applyEvent(re, result, event));
      }
    }
    long version = (aggregate == null ? 0 : aggregate.version()) + events.size();
    return VersionStamp.withVersion(type, result, version);
  }

  private <E extends Event, A extends Aggregate> A applyEvent(
      RegisteredEvent<E, A> re, Aggregate current, Event event) {
    return re.handler().apply(re.aggregateType().cast(current), re.id().type().cast(event));
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

  private <I, Q extends Query, T> Object resolveDep(
      DepBinding<I, Q, T> binding, Context ctx, I input, RequestMeta meta, RequestCache cache) {
    Dep<Q, T> dep = binding.key();
    Q q = binding.queryFn().apply(ctx, input);
    RequestCache.DepKey key = new RequestCache.DepKey(dep.service(), q);
    if (cache.hasDep(key)) {
      return cache.dep(key);
    }
    Object result =
        (dep.service() != null && !dep.service().equals(serviceName))
            ? remoteClient.query(dep.service(), dep.queryId(), q, meta)
            : queryByType(q, meta);
    cache.putDep(key, result);
    return result;
  }

  private List<Command> runFx(Context ctx, List<Event> events) {
    List<Command> all = new ArrayList<>();
    for (Event event : events) {
      List<RegisteredFx<?>> handlers = eventFxByType.get(event.getClass());
      if (handlers == null) {
        continue;
      }
      for (RegisteredFx<?> fx : handlers) {
        all.addAll(invokeFx(fx, ctx, event));
      }
    }
    return all;
  }

  private <E extends Event> List<Command> invokeFx(RegisteredFx<E> fx, Context ctx, Event event) {
    return fx.handler().fx(ctx, fx.id().type().cast(event));
  }

  private Object queryByType(Query q, RequestMeta meta) {
    RegisteredQuery<?, ?> reg = queriesByType.get(q.getClass());
    if (reg == null) {
      throw new IllegalStateException("No query handler registered for " + q.getClass().getName());
    }
    return runQuery(reg, q, meta);
  }

  private <Q extends Query, R> R runQuery(
      RegisteredQuery<Q, R> reg, Query rawQuery, RequestMeta meta) {
    Q query = reg.id().queryType().cast(rawQuery);
    Map<String, Object> resolved = new LinkedHashMap<>();
    Context ctx = new ContextImpl<>(resolved, meta, null, viewStore, Aggregate.class);
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
    for (CommandSpec<?, ?> spec : commandsByType.values()) {
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

  record RegisteredFx<E extends Event>(EventId<E> id, EventFxHandler<E> handler) {}

  record RegisteredQuery<Q extends Query, R>(
      QueryId<Q, R> id,
      QueryHandler<Q, R> handler,
      List<DepBinding<Q, ?, ?>> deps,
      Schema<? super R> produces) {}

  public static final class Builder {

    private final String serviceName;
    private final Map<CommandId<?>, CommandSpec<?, ?>> commands = new LinkedHashMap<>();
    private final Map<EventId<?>, RegisteredEvent<?, ?>> events = new LinkedHashMap<>();
    private final Map<EventId<?>, List<RegisteredFx<?>>> eventFx = new LinkedHashMap<>();
    private final Map<QueryId<?, ?>, RegisteredQuery<?, ?>> queries = new LinkedHashMap<>();
    private final Map<Class<?>, Schema<Object>> aggregateSchemas = new LinkedHashMap<>();
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
    // Default: retry concurrent-modification up to 3 attempts, no backoff (the conflicting writer
    // has
    // already committed, so an immediate retry against fresh state typically succeeds).
    private RetryConfig retryConfig =
        RetryConfig.builder().maxAttempts(3).retryOn(OptimisticLockException.class).build();

    private Builder(String serviceName) {
      this.serviceName = serviceName;
    }

    /**
     * Tune the concurrent-modification retry (attempts, backoff). Default: 3 attempts, no backoff.
     */
    public Builder retryConfig(RetryConfig retryConfig) {
      this.retryConfig = Objects.requireNonNull(retryConfig, "retryConfig");
      return this;
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
      eventFx.computeIfAbsent(id, k -> new ArrayList<>()).add(new RegisteredFx<>(id, handler));
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
      aggregateSchemas.put(aggregateType, state -> schema.violations(aggregateType.cast(state)));
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
