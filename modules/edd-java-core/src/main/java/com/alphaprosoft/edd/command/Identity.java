package com.alphaprosoft.edd.command;

import java.util.Objects;
import java.util.UUID;

/**
 * A uniqueness reservation: a natural key ({@code name}) bound to an aggregate. Mirrors edd-core's
 * {@code {:identity … :id …}}. The handler supplies the {@code name} via the builder; {@code
 * aggregateId} is stamped by the dispatcher from the command's aggregate id when left null
 * (edd-core's {@code (assoc % :id (:id cmd))}).
 *
 * <p>Built through {@link #builder()} so future fields (metadata, …) stay source-compatible.
 */
public record Identity(String name, UUID aggregateId) implements CommandEmission {

  public Identity {
    Objects.requireNonNull(name, "name");
  }

  public Identity withAggregateId(UUID aggregateId) {
    return new Identity(name, aggregateId);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(Identity existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private String name;
    private UUID aggregateId;

    private Builder() {}

    private Builder(Identity i) {
      this.name = i.name;
      this.aggregateId = i.aggregateId;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder aggregateId(UUID aggregateId) {
      this.aggregateId = aggregateId;
      return this;
    }

    public Identity build() {
      return new Identity(name, aggregateId);
    }
  }
}
