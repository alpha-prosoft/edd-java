package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

/** Read a pong aggregate by id. queryId {@code get-pong}. */
public record GetPongQuery(UUID id) implements Query {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public GetPongQuery build() {
      return new GetPongQuery(id);
    }
  }
}
