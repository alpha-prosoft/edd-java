package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/**
 * Set the ping aggregate's value. cmdId {@code ping-set-value}. The {@code version} component
 * overrides {@link Command#version()} — when non-null the dispatcher enforces optimistic
 * concurrency.
 */
public record PingSetValueCommand(UUID id, Long version, String value) implements Command {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private Long version;
    private String value;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder version(Long version) {
      this.version = version;
      return this;
    }

    public Builder value(String value) {
      this.value = value;
      return this;
    }

    public PingSetValueCommand build() {
      return new PingSetValueCommand(id, version, value);
    }
  }
}
