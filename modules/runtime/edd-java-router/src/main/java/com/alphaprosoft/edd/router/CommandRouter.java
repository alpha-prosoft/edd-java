package com.alphaprosoft.edd.router;

import com.alphaprosoft.edd.core.config.Config;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The CQRS effect router. Services send every produced effect (a {@code {cmdId,command,meta}}
 * message) to one shared router queue; this consumer reads each message, looks up the owning
 * service's command queue by {@code cmdId}, and forwards the body verbatim — it never deserializes
 * the command, so it needs no domain classes on its classpath.
 *
 * <p>Because edd-java effects are plain commands with no target-service tag, a routed {@code cmdId}
 * must be globally unique and map to exactly one queue. An unknown {@code cmdId} is reported as a
 * partial- batch failure (the SQS source redelivers that record) rather than silently dropped.
 *
 * <p>Returns the AWS standard partial-batch response ({@code
 * {batchItemFailures:[{itemIdentifier}]}}): only records that failed to route are reported (and
 * redelivered).
 */
public final class CommandRouter {

  static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private final Map<String, String> routes;
  private final Sender sender;

  private CommandRouter(Map<String, String> routes, Sender sender) {
    this.routes = Map.copyOf(routes);
    this.sender = sender;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Build from configuration: {@code router.routes} is a JSON object string of {@code cmdId ->
   * queueUrl}.
   */
  public static CommandRouter fromConfig(Config config) {
    String json = config.get("router.routes", "{}");
    try {
      Map<String, String> routes =
          MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
      return builder().routes(routes).sender(new SqsSender()).build();
    } catch (Exception e) {
      throw new IllegalStateException("Invalid router.routes JSON: " + json, e);
    }
  }

  public String handle(String eventJson) {
    JsonNode event;
    try {
      event = MAPPER.readTree(eventJson);
    } catch (Exception e) {
      throw new IllegalStateException("Bad SQS event JSON", e);
    }
    List<Map<String, String>> failures = new ArrayList<>();
    for (JsonNode record : event.get("Records")) {
      String messageId = record.path("messageId").asText(null);
      try {
        String body = record.get("body").asText();
        String cmdId = MAPPER.readTree(body).get("cmdId").asText();
        String queueUrl = routes.get(cmdId);
        if (queueUrl == null) {
          throw new IllegalStateException("No route for cmdId '" + cmdId + "'");
        }
        sender.send(queueUrl, body);
      } catch (Exception e) {
        failures.add(Map.of("itemIdentifier", messageId == null ? "" : messageId));
      }
    }
    try {
      return MAPPER.writeValueAsString(Map.of("batchItemFailures", failures));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write router response", e);
    }
  }

  public void handle(InputStream input, OutputStream output) throws IOException {
    output.write(
        handle(new String(input.readAllBytes(), StandardCharsets.UTF_8))
            .getBytes(StandardCharsets.UTF_8));
  }

  public static final class Builder {
    private Map<String, String> routes = Map.of();
    private Sender sender;

    private Builder() {}

    public Builder routes(Map<String, String> routes) {
      this.routes = routes;
      return this;
    }

    public Builder sender(Sender sender) {
      this.sender = sender;
      return this;
    }

    public CommandRouter build() {
      if (sender == null) {
        throw new IllegalStateException("sender is required");
      }
      return new CommandRouter(routes, sender);
    }
  }
}
