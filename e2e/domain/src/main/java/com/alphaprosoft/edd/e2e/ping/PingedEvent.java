package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** eventId {@code pinged}. */
public record PingedEvent(UUID id, long hops) implements Event {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private long hops;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder hops(long hops) {
      this.hops = hops;
      return this;
    }

    public PingedEvent build() {
      return new PingedEvent(id, hops);
    }
  }
}
