package com.example.sample;

import com.alphaprosoft.edd.core.Aggregate;
import java.util.UUID;

/** State folded from events. {@code version} is owned by the framework — apply methods leave it 0. */
public record SampleAggregate(UUID id, long version, String name, long revision)
    implements Aggregate {

  public static SampleAggregate created(SampleAggregate current, SampleCreatedEvent event) {
    long previous = current == null ? 0 : current.revision();
    return new SampleAggregate(event.id(), 0, event.name(), previous + 1);
  }
}
