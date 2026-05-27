package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/**
 * Set the pong aggregate's value. cmdId {@code pong-set-value}. Emitted as a fan-out effect by
 * ping-svc.
 */
public record PongSetValueCommand(UUID id, String value) implements Command {

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

    public PongSetValueCommand build() {
      return new PongSetValueCommand(id, value);
    }
  }
}
