package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.core.Aggregate;
import java.util.UUID;

public record GreeterAggregate(UUID id, long version, String greeting) implements Aggregate {

  public static GreeterAggregate greeted(GreeterAggregate agg, CustomerGreetedEvent e) {
    return new GreeterAggregate(e.id(), 0, e.greeting());
  }
}
