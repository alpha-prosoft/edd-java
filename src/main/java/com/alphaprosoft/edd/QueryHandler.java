package com.alphaprosoft.edd;

@FunctionalInterface
public interface QueryHandler<Q extends Query, R> {
    R handle(Context ctx, Q query);
}
