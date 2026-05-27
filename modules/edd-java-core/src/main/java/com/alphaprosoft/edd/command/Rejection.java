package com.alphaprosoft.edd.command;

import java.util.Map;
import java.util.Objects;

/**
 * A command failure emitted by a handler. Any rejection in the returned list fails the whole
 * command — events and identities are discarded — matching edd-core's "if any item has {@code
 * :error}, the response is an error".
 */
public record Rejection(String code, Map<String, Object> details) implements CommandEmission {

  public Rejection {
    Objects.requireNonNull(code, "code");
    details = details == null ? Map.of() : Map.copyOf(details);
  }

  public static Rejection of(String code) {
    return new Rejection(code, Map.of());
  }

  public static Rejection of(String code, Map<String, Object> details) {
    return new Rejection(code, details);
  }
}
