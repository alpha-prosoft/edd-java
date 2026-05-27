package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

/**
 * Read a ping aggregate by id. queryId {@code get-ping}. Used both by tests and as pong's remote
 * dep.
 */
public record GetPingQuery(UUID id) implements Query {

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

    public GetPingQuery build() {
      return new GetPingQuery(id);
    }
  }
}
