package com.alphaprosoft.edd.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EddServerTest {

  public record PingQuery(String message) implements Query {}

  static final QueryId<PingQuery, String> PING = QueryId.of("ping", PingQuery.class, String.class);

  private static EddServer server;
  private static int port;

  @BeforeAll
  static void start() throws IOException {
    port = freePort();
    Application app =
        Application.builder("ping-svc").regQuery(PING, (ctx, q) -> "pong:" + q.message()).build();
    server = new EddServer(app, port, Tls.serverContext());
    server.start();
  }

  @AfterAll
  static void stop() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void queryOverHttp2() throws Exception {
    HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(Tls.trustAllContext())
            .build();
    HttpResponse<String> resp =
        client.send(
            request(
                HttpClient.Version.HTTP_2,
                Map.of("queryId", "ping", "query", new PingQuery("hi"), "meta", Map.of())),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(HttpClient.Version.HTTP_2, resp.version());
    assertEquals(200, resp.statusCode());
    JsonNode root = Wire.MAPPER.readTree(resp.body());
    assertEquals("pong:hi", root.get("result").asText());
  }

  @Test
  void http1IsRejected() throws Exception {
    HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(Tls.trustAllContext())
            .build();
    HttpResponse<String> resp =
        client.send(
            request(
                HttpClient.Version.HTTP_1_1,
                Map.of("queryId", "ping", "query", new PingQuery("x"), "meta", Map.of())),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(505, resp.statusCode());
    assertTrue(resp.body().contains("HTTP/2 required"));
  }

  @Test
  void healthCheckWorksOverHttp1() throws Exception {
    HttpClient http1 =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(Tls.trustAllContext())
            .build();
    HttpResponse<String> resp =
        http1.send(
            HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/health"))
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, resp.statusCode(), "load-balancer probe must pass even without HTTP/2");
    assertTrue(resp.body().contains("ok"));
  }

  @Test
  void queryOverGetWithQueryString() throws Exception {
    HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(Tls.trustAllContext())
            .build();
    HttpResponse<String> resp =
        client.send(
            HttpRequest.newBuilder(
                    URI.create("https://localhost:" + port + "/api/query?queryId=ping&message=hey"))
                .version(HttpClient.Version.HTTP_2)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, resp.statusCode());
    assertEquals("pong:hey", Wire.MAPPER.readTree(resp.body()).get("result").asText());
  }

  @Test
  void corsHeadersAndPreflight() throws Exception {
    HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(Tls.trustAllContext())
            .build();
    HttpResponse<String> preflight =
        client.send(
            HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/api/query"))
                .version(HttpClient.Version.HTTP_2)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(204, preflight.statusCode());
    assertEquals("*", preflight.headers().firstValue("access-control-allow-origin").orElse(null));
  }

  @Test
  void gzipResponseWhenAccepted() throws Exception {
    HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(Tls.trustAllContext())
            .build();
    HttpResponse<byte[]> resp =
        client.send(
            HttpRequest.newBuilder(
                    URI.create("https://localhost:" + port + "/api/query?queryId=ping&message=z"))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept-Encoding", "gzip")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());

    assertEquals(200, resp.statusCode());
    assertEquals(
        "gzip",
        resp.headers().firstValue("content-encoding").orElse(null),
        "server gzipped the response");
  }

  private static HttpRequest request(HttpClient.Version version, Object body) throws Exception {
    return HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/api/query"))
        .version(version)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(Wire.MAPPER.writeValueAsString(body)))
        .build();
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
