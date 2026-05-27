package com.alphaprosoft.edd.command;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface CommandResponse {

  record Success(
      UUID aggregateId, List<Event> events, List<Identity> identities, List<Command> effects)
      implements CommandResponse {
    public Success {
      events = List.copyOf(events);
      identities = List.copyOf(identities);
      effects = List.copyOf(effects);
    }
  }

  /**
   * A command failure. {@code code}/{@code details} describe the primary cause; {@code rejections}
   * holds every {@link Rejection} a handler emitted (edd-core collects all errors, not just the
   * first). Framework failures (concurrent-modification, identity-conflict, invalid-command) carry
   * an empty {@code rejections} list.
   */
  record Failure(String code, Map<String, Object> details, List<Rejection> rejections)
      implements CommandResponse {
    public Failure {
      details = Map.copyOf(details);
      rejections = List.copyOf(rejections);
    }

    public Failure(String code, Map<String, Object> details) {
      this(code, details, List.of());
    }
  }

  /** Build a failure from all rejections a handler returned, preserving each one. */
  static Failure rejected(List<Rejection> rejections) {
    if (rejections.isEmpty()) {
      throw new IllegalArgumentException("rejected requires at least one rejection");
    }
    if (rejections.size() == 1) {
      Rejection only = rejections.getFirst();
      return new Failure(only.code(), only.details(), rejections);
    }
    List<Map<String, Object>> all =
        rejections.stream()
            .map(r -> Map.<String, Object>of("code", r.code(), "details", r.details()))
            .toList();
    return new Failure("rejected", Map.of("rejections", all), rejections);
  }
}
