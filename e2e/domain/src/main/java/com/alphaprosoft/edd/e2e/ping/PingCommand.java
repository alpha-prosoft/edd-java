package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/** Start (or continue) a ping↔pong hop. cmdId {@code ping}. */
public record PingCommand(UUID id, long hops) implements Command {

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

    public PingCommand build() {
      return new PingCommand(id, hops);
    }
  }
}
