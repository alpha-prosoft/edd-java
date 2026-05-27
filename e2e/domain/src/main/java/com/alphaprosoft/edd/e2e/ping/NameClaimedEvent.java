package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** eventId {@code ping-name-claimed}. */
public record NameClaimedEvent(UUID id, String name) implements Event {

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

    public NameClaimedEvent build() {
      return new NameClaimedEvent(id, name);
    }
  }
}
