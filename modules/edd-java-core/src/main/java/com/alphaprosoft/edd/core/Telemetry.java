package com.alphaprosoft.edd.core;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A structured-event sink for observability — the correlation-aware logging seam (edd-core's MDC +
 * logback). The engine emits named events ({@code command.received}, {@code command.succeeded},
 * {@code command.failed}, {@code query.received}, …) with a field map that always carries the
 * correlation ids ({@code requestId}, {@code interactionId}, {@code realm}, {@code breadcrumbs}).
 *
 * <p>{@link #NONE} discards everything (the default, so libraries stay quiet); {@link #stdout()}
 * writes one JSON line per event; tests can capture events with a small lambda.
 */
@FunctionalInterface
public interface Telemetry {

  void emit(String event, Map<String, Object> fields);

  Telemetry NONE = (event, fields) -> {};

  static Telemetry stdout() {
    System.Logger log = System.getLogger("edd.telemetry");
    return (event, fields) -> log.log(System.Logger.Level.INFO, toJson(event, fields));
  }

  private static String toJson(String event, Map<String, Object> fields) {
    String body =
        fields.entrySet().stream()
            .map(e -> quote(e.getKey()) + ":" + value(e.getValue()))
            .collect(Collectors.joining(","));
    return "{" + quote("event") + ":" + quote(event) + (body.isEmpty() ? "" : "," + body) + "}";
  }

  private static String value(Object v) {
    return switch (v) {
      case null -> "null";
      case Number n -> n.toString();
      case Boolean b -> b.toString();
      default -> quote(v.toString());
    };
  }

  private static String quote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
