package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.CommandResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;

/**
 * API Gateway ingestion. The proxy event's {@code body} is either a {@code {cmdId,command,meta}}
 * command message or a {@code {queryId,query,meta}} read; commands dispatch + route effects through
 * the {@link Processor}, reads resolve through the {@link Querier}. Returns the user's HTTP
 * response (API GW proxy shape): 200 on success ({@code {result:…}} for a query), 422 on a business
 * rejection, 500 on error. A {@code GET …/health} probe is answered with 200 directly (no command
 * dispatched).
 */
public final class ApiFilter implements IngestFilter {

  private final Querier querier;

  /** Command-only filter (queries return a 500). */
  public ApiFilter() {
    this(null);
  }

  public ApiFilter(Querier querier) {
    this.querier = querier;
  }

  @Override
  public boolean handles(JsonNode event) {
    return event.hasNonNull("requestContext") || event.hasNonNull("httpMethod");
  }

  @Override
  public Object process(JsonNode event, Processor processor) {
    if (isHealthCheck(event)) {
      return Map.of(
          "statusCode",
          200,
          "headers",
          Map.of("content-type", "application/json"),
          "body",
          "{\"status\":\"ok\"}");
    }
    int status;
    String body;
    try {
      JsonNode message = Messages.parse(event.get("body").asText());
      if (message.hasNonNull("queryId")) {
        if (querier == null) {
          throw new IllegalStateException("Query received but no Querier is configured");
        }
        Object result =
            querier.query(
                message.get("queryId").asText(), message.get("query"), Messages.meta(message));
        status = 200;
        // a query may legitimately return null (aggregate not found); Map.of rejects null
        body = Messages.MAPPER.writeValueAsString(Collections.singletonMap("result", result));
      } else {
        Inbound in = Messages.decodeCommand(message);
        CommandResponse response = processor.handle(in.command(), in.meta());
        status = response instanceof CommandResponse.Failure ? 422 : 200;
        body = Messages.MAPPER.writeValueAsString(Messages.summary(response));
      }
    } catch (Exception e) {
      status = 500;
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      body = "{\"error\":" + quote(message) + "}";
    }
    return Map.of(
        "statusCode", status,
        "headers", Map.of("content-type", "application/json"),
        "body", body);
  }

  private static boolean isHealthCheck(JsonNode event) {
    String path =
        event.hasNonNull("path")
            ? event.get("path").asText()
            : event
                .path("rawPath")
                .asText(event.path("requestContext").path("http").path("path").asText(""));
    return path.endsWith("/health");
  }

  private static String quote(String s) {
    return "\"" + String.valueOf(s).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
