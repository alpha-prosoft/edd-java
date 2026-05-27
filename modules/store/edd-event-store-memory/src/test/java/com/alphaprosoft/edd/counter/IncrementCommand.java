package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/** {@code version} drives optimistic concurrency; pass null to skip the check. */
public record IncrementCommand(UUID id, long amount, Long version) implements Command {

  public static IncrementCommand of(UUID id, long amount) {
    return new IncrementCommand(id, amount, null);
  }
}
