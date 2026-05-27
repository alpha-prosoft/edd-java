package com.alphaprosoft.edd.command;

import java.util.UUID;

public interface Command {

  UUID id();

  /**
   * Aggregate version this command expects, for optimistic concurrency. Default {@code null} means
   * "don't check". A command record can supply it with a {@code version} component. Mirrors
   * edd-core's {@code (:version cmd)} / {@code verify-command-version}.
   */
  default Long version() {
    return null;
  }
}
