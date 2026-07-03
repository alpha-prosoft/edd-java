package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.core.Aggregate;

/**
 * A query handler registered through a module: like {@link QueryHandler} but receives a {@link
 * QueryContext} bound to the module's aggregate type, so {@code ctx.getAggregate(id)} needs no type
 * token.
 */
@FunctionalInterface
public interface AggregateQueryHandler<Q extends Query, R, A extends Aggregate> {
  R handle(QueryContext<A> ctx, Q query);
}
