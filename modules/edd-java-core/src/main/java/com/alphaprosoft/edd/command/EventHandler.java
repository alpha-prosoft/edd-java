package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.Aggregate;

@FunctionalInterface
public interface EventHandler<E extends Event, A extends Aggregate> {
  A apply(A aggregate, E event);
}
