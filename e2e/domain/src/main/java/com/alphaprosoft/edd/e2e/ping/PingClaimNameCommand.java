package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/** Claim a unique name (reserves an identity). cmdId {@code ping-claim-name}. */
public record PingClaimNameCommand(UUID id, String name) implements Command {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private String name;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public PingClaimNameCommand build() {
      return new PingClaimNameCommand(id, name);
    }
  }
}
