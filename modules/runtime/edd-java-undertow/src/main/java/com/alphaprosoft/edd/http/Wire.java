package com.alphaprosoft.edd.http;

import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.core.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JSON wire format shared by the HTTP server and client: a single {@link ObjectMapper} (records
 * serialize natively) plus {@link RequestMeta} <-> JSON conversion.
 */
public final class Wire {

  public static final ObjectMapper MAPPER = new ObjectMapper();

  private Wire() {}

  public static RequestMeta parseMeta(JsonNode m) {
    if (m == null || m.isNull()) {
      return RequestMeta.newRequest();
    }
    RequestMeta.Builder b = RequestMeta.builder();
    if (m.hasNonNull("requestId")) {
      b.requestId(UUID.fromString(m.get("requestId").asText()));
    }
    if (m.hasNonNull("interactionId")) {
      b.interactionId(UUID.fromString(m.get("interactionId").asText()));
    }
    if (m.hasNonNull("realm")) {
      b.realm(m.get("realm").asText());
    }
    JsonNode user = m.get("user");
    if (user != null && !user.isNull()) {
      String id = user.hasNonNull("id") ? user.get("id").asText() : null;
      String role = user.hasNonNull("role") ? user.get("role").asText() : "anonymous";
      b.user(User.of(id, role));
    }
    JsonNode annotations = m.get("annotations");
    if (annotations != null && annotations.isObject()) {
      annotations.fields().forEachRemaining(e -> b.annotation(e.getKey(), e.getValue().asText()));
    }
    return b.build();
  }

  public static Map<String, Object> metaJson(RequestMeta meta) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("requestId", meta.requestId().toString());
    out.put("interactionId", meta.interactionId().toString());
    out.put("realm", meta.realm());
    User user = meta.user();
    if (user != null) {
      Map<String, Object> u = new LinkedHashMap<>();
      u.put("id", user.id());
      u.put("role", user.role());
      out.put("user", u);
    }
    out.put("annotations", meta.annotations());
    return out;
  }
}
