package com.alphaprosoft.edd.e2e.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Thin client for the deployed ping/pong API, speaking the Java edd wire format: commands are
 * {@code {cmdId,command,meta}} (response {@code {status,aggregateId,events,effects}}), queries are
 * {@code {queryId,query,meta}} (response {@code {result:…}}). The base URL is read from the {@code
 * ApiUrl} env var; absence is a hard failure (the suite only runs against a live deploy).
 */
final class Api {

  static final ObjectMapper M = new ObjectMapper();
  static final String BASE = required("ApiUrl");

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private Api() {}

  record Resp(int status, JsonNode body) {
    boolean success() {
      return status == 200 && "success".equals(body.path("status").asText());
    }

    String code() {
      return body.path("code").asText();
    }
  }

  static String required(String env) {
    String v = System.getenv(env);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException("env " + env + " must be set — deploy with run-e2e.sh first");
    }
    return v;
  }

  static Map<String, Object> meta(UUID requestId) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("requestId", requestId.toString());
    m.put("interactionId", UUID.randomUUID().toString());
    m.put("realm", "test");
    return m;
  }

  static Resp command(String svc, String cmdId, Map<String, Object> command, UUID requestId) {
    Map<String, Object> msg = new LinkedHashMap<>();
    msg.put("cmdId", cmdId);
    msg.put("command", command);
    msg.put("meta", meta(requestId));
    return post("/" + svc + "/commands", msg);
  }

  static Resp command(String svc, String cmdId, Map<String, Object> command) {
    return command(svc, cmdId, command, UUID.randomUUID());
  }

  /** Run a query and return its {@code result} node (null node if absent). */
  static JsonNode query(String svc, String queryId, Map<String, Object> query) {
    Map<String, Object> msg = new LinkedHashMap<>();
    msg.put("queryId", queryId);
    msg.put("query", query);
    msg.put("meta", meta(UUID.randomUUID()));
    return post("/" + svc + "/query", msg).body().path("result");
  }

  static JsonNode aggregate(String svc, String getQueryId, UUID id) {
    return query(svc, getQueryId, Map.of("id", id.toString()));
  }

  private static Resp post(String path, Map<String, Object> body) {
    try {
      String json = M.writeValueAsString(body);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(BASE + path))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      return new Resp(response.statusCode(), M.readTree(response.body()));
    } catch (Exception e) {
      throw new RuntimeException("POST " + path + " failed", e);
    }
  }

  /**
   * Poll {@code f} until {@code pred} holds (tolerates async router round-trips + DynamoDB lag).
   */
  static <T> T waitFor(Predicate<T> pred, Supplier<T> f) {
    T last = null;
    for (int n = 0; n < 30; n++) {
      last = f.get();
      if (pred.test(last)) {
        return last;
      }
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return last;
  }

  static <T> T waitFor(
      Function<JsonNode, T> extract, Predicate<T> pred, String svc, String getQueryId, UUID id) {
    return waitFor(pred, () -> extract.apply(aggregate(svc, getQueryId, id)));
  }
}
