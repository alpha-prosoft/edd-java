package com.alphaprosoft.edd;

@FunctionalInterface
public interface EventHandler<E extends Event, A extends Aggregate> {
    A apply(A aggregate, E event);
}
