package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.core.Aggregate;
import java.util.Optional;
import java.util.UUID;

public record CounterAggregate(UUID id, long version, long count, String name)
    implements Aggregate {

  public static CounterAggregate incremented(CounterAggregate agg, IncrementedEvent e) {
    long count = Optional.ofNullable(agg).map(CounterAggregate::count).orElse(0L) + e.amount();
    String name = Optional.ofNullable(agg).map(CounterAggregate::name).orElse(null);
    return new CounterAggregate(e.id(), 0, count, name);
  }

  public static CounterAggregate nameClaimed(CounterAggregate agg, NameClaimedEvent e) {
    long count = Optional.ofNullable(agg).map(CounterAggregate::count).orElse(0L);
    return new CounterAggregate(e.id(), 0, count, e.name());
  }
}
