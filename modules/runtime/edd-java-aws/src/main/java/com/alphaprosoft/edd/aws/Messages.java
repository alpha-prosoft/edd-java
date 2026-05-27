package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.RequestMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The SQS message wire format for a command/effect: {@code {cmdId, command, meta}}. The command
 * class is resolved from {@code cmdId} via the {@link CommandId} registry, so the body is plain
 * JSON with no type tags. {@code meta} is a serialized {@link RequestMeta} (carries
 * realm/user/breadcrumbs).
 */
final class Messages {

  static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private Messages() {}

  static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("Bad message JSON", e);
    }
  }

  static Inbound decodeCommand(JsonNode node) {
    String cmdId = node.get("cmdId").asText();
    Class<?> type =
        CommandId.lookup(cmdId)
            .orElseThrow(() -> new IllegalStateException("Unknown cmdId: " + cmdId))
            .type();
    Command command = (Command) MAPPER.convertValue(node.get("command"), type);
    return new Inbound(command, meta(node));
  }

  static RequestMeta meta(JsonNode node) {
    return node.hasNonNull("meta")
        ? MAPPER.convertValue(node.get("meta"), RequestMeta.class)
        : RequestMeta.newRequest();
  }

  static String encodeCommand(Command command, RequestMeta meta) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("cmdId", CommandId.forType(command.getClass()).id());
    message.put("command", command);
    message.put("meta", meta);
    try {
      return MAPPER.writeValueAsString(message);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encode command message", e);
    }
  }

  static Map<String, Object> summary(CommandResponse response) {
    Map<String, Object> out = new LinkedHashMap<>();
    switch (response) {
      case CommandResponse.Success s -> {
        out.put("status", "success");
        out.put("aggregateId", s.aggregateId());
        out.put("events", s.events().size());
        out.put("effects", s.effects().size());
      }
      case CommandResponse.Failure f -> {
        out.put("status", "failure");
        out.put("code", f.code());
      }
    }
    return out;
  }

  static JsonNode firstRecord(JsonNode event) {
    JsonNode records = event.get("Records");
    return records != null && records.isArray() && !records.isEmpty() ? records.get(0) : null;
  }

  static String text(JsonNode node, String field) {
    return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
  }
}
