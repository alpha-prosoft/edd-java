package com.alphaprosoft.edd.crossservice;

import com.alphaprosoft.edd.core.Aggregate;
import java.util.UUID;

public record GreeterAggregate(UUID id, long version, String greeting) implements Aggregate {

  public static GreeterAggregate greeted(GreeterAggregate agg, GreetedEvent e) {
    return new GreeterAggregate(e.id(), 0, e.greeting());
  }
}
