package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/**
 * Set a score; validated by a {@code consumes} schema (score must be positive). cmdId {@code
 * ping-set-score}.
 */
public record PingSetScoreCommand(UUID id, long score) implements Command {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private long score;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder score(long score) {
      this.score = score;
      return this;
    }

    public PingSetScoreCommand build() {
      return new PingSetScoreCommand(id, score);
    }
  }
}
