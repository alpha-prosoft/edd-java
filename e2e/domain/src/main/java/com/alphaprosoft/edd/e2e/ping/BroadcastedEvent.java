package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/**
 * eventId {@code ping-broadcasted}. Carries the two fan-out targets so the effect can address both
 * services.
 */
public record BroadcastedEvent(UUID id, UUID pingTarget, UUID pongTarget, String value)
    implements Event {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private UUID pingTarget;
    private UUID pongTarget;
    private String value;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder pingTarget(UUID pingTarget) {
      this.pingTarget = pingTarget;
      return this;
    }

    public Builder pongTarget(UUID pongTarget) {
      this.pongTarget = pongTarget;
      return this;
    }

    public Builder value(String value) {
      this.value = value;
      return this;
    }

    public BroadcastedEvent build() {
      return new BroadcastedEvent(id, pingTarget, pongTarget, value);
    }
  }
}
