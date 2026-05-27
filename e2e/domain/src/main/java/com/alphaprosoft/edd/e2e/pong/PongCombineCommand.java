package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/**
 * Combine a local value with ping's aggregate, fetched over the wire from ping-svc. cmdId {@code
 * pong-combine}.
 */
public record PongCombineCommand(UUID id, UUID pingId, String value) implements Command {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private UUID pingId;
    private String value;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder pingId(UUID pingId) {
      this.pingId = pingId;
      return this;
    }

    public Builder value(String value) {
      this.value = value;
      return this;
    }

    public PongCombineCommand build() {
      return new PongCombineCommand(id, pingId, value);
    }
  }
}
