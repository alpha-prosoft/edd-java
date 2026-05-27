package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/**
 * Fan a value out to one ping aggregate and one pong aggregate (multi-service effects). cmdId
 * {@code ping-broadcast}.
 */
public record PingBroadcastCommand(UUID id, UUID pingTarget, UUID pongTarget, String value)
    implements Command {

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

    public PingBroadcastCommand build() {
      return new PingBroadcastCommand(id, pingTarget, pongTarget, value);
    }
  }
}
