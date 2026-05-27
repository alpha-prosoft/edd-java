package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** eventId {@code pong-combined}. Captures the ping value+version observed at combine time. */
public record CombinedEvent(UUID id, String pingValue, long pingVersion, String pongValue)
    implements Event {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private String pingValue;
    private long pingVersion;
    private String pongValue;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder pingValue(String pingValue) {
      this.pingValue = pingValue;
      return this;
    }

    public Builder pingVersion(long pingVersion) {
      this.pingVersion = pingVersion;
      return this;
    }

    public Builder pongValue(String pongValue) {
      this.pongValue = pongValue;
      return this;
    }

    public CombinedEvent build() {
      return new CombinedEvent(id, pingValue, pingVersion, pongValue);
    }
  }
}
