package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** eventId {@code ping-value-set}. */
public record PingValueSetEvent(UUID id, String value) implements Event {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private String value;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder value(String value) {
      this.value = value;
      return this;
    }

    public PingValueSetEvent build() {
      return new PingValueSetEvent(id, value);
    }
  }
}
