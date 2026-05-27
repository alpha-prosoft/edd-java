package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.command.CommandSpec;
import com.alphaprosoft.edd.core.Context;
import com.alphaprosoft.edd.core.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A query registration with its own dependencies, mirroring edd-core's {@code (reg-query :id
 * handler :deps {…})}. Deps are resolved into the {@link Context} before the handler runs; read
 * them with {@code ctx.getDeps(KEY)}.
 *
 * <p>Unlike {@link CommandSpec} the handler is an instance (queries are lambdas, not per-dispatch
 * classes) and there is no aggregate or id function.
 */
public record QuerySpec<Q extends Query, R>(
    QueryId<Q, R> queryId,
    QueryHandler<Q, R> handler,
    List<DepBinding<Q, ?, ?>> deps,
    Schema<? super R> produces) {

  public QuerySpec {
    Objects.requireNonNull(queryId, "queryId");
    Objects.requireNonNull(handler, "handler");
    deps = deps == null ? List.of() : List.copyOf(deps);
  }

  public QuerySpec(
      QueryId<Q, R> queryId, QueryHandler<Q, R> handler, List<DepBinding<Q, ?, ?>> deps) {
    this(queryId, handler, deps, null);
  }

  public static <Q extends Query, R> Init<Q, R> builder(QueryId<Q, R> queryId) {
    return new StagedBuilder<>(queryId);
  }

  /** First stage — a handler is required before deps can reference {@code Q}. */
  public sealed interface Init<Q extends Query, R> permits StagedBuilder {
    Builder<Q, R> handler(QueryHandler<Q, R> handler);
  }

  /** Second stage — {@code Q} is fixed, so {@code dep(...)} lambdas type {@code query}. */
  public sealed interface Builder<Q extends Query, R> permits StagedBuilder {

    <D extends Query, T> Builder<Q, R> dep(
        Dep<D, T> key, BiFunction<Context, ? super Q, D> queryFn);

    /** Validate the handler's result before returning it (edd-core {@code :produces}). */
    Builder<Q, R> produces(Schema<? super R> produces);

    QuerySpec<Q, R> build();
  }

  private static final class StagedBuilder<Q extends Query, R>
      implements Init<Q, R>, Builder<Q, R> {

    private final QueryId<Q, R> queryId;
    private QueryHandler<Q, R> handler;
    private final List<DepBinding<Q, ?, ?>> deps = new ArrayList<>();
    private Schema<? super R> produces;

    StagedBuilder(QueryId<Q, R> queryId) {
      this.queryId = queryId;
    }

    @Override
    public Builder<Q, R> handler(QueryHandler<Q, R> handler) {
      this.handler = handler;
      return this;
    }

    @Override
    public <D extends Query, T> Builder<Q, R> dep(
        Dep<D, T> key, BiFunction<Context, ? super Q, D> queryFn) {
      deps.add(new DepBinding<>(key, queryFn));
      return this;
    }

    @Override
    public Builder<Q, R> produces(Schema<? super R> produces) {
      this.produces = produces;
      return this;
    }

    @Override
    public QuerySpec<Q, R> build() {
      return new QuerySpec<>(queryId, handler, deps, produces);
    }
  }
}
