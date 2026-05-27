package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** eventId {@code ping-score-set}. */
public record ScoreSetEvent(UUID id, long score) implements Event {

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

    public ScoreSetEvent build() {
      return new ScoreSetEvent(id, score);
    }
  }
}
