package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.core.Context;

@FunctionalInterface
public interface QueryHandler<Q extends Query, R> {
  R handle(Context ctx, Q query);
}
