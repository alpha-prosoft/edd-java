package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.command.Identity;
import java.util.UUID;

/** Thrown when a uniqueness {@link Identity} is already reserved by a different aggregate. */
public final class IdentityConflictException extends RuntimeException {

  private final String name;
  private final UUID existingAggregateId;
  private final UUID attemptedAggregateId;

  public IdentityConflictException(
      String name, UUID existingAggregateId, UUID attemptedAggregateId) {
    super(
        "Identity '"
            + name
            + "' already reserved by "
            + existingAggregateId
            + " (attempted "
            + attemptedAggregateId
            + ")");
    this.name = name;
    this.existingAggregateId = existingAggregateId;
    this.attemptedAggregateId = attemptedAggregateId;
  }

  public String name() {
    return name;
  }

  public UUID existingAggregateId() {
    return existingAggregateId;
  }

  public UUID attemptedAggregateId() {
    return attemptedAggregateId;
  }
}
