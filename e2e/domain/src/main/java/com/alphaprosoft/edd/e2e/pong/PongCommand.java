package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/** Continue a ping↔pong hop. cmdId {@code pong}. Emitted as an effect by ping-svc, routed here. */
public record PongCommand(UUID id, long hops) implements Command {

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

    public PongCommand build() {
      return new PongCommand(id, hops);
    }
  }
}
