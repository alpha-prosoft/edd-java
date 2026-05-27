package com.alphaprosoft.edd.order.query;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

public record GetProductQuery(UUID id) implements Query {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(GetProductQuery existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;

    private Builder() {}

    private Builder(GetProductQuery q) {
      this.id = q.id;
    }

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public GetProductQuery build() {
      return new GetProductQuery(id);
    }
  }
}
