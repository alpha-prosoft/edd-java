package com.alphaprosoft.edd.demo.greeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.core.RemoteServiceClient;
import com.alphaprosoft.edd.demo.customer.CustomerApp;
import com.alphaprosoft.edd.demo.customer.CustomerIds;
import com.alphaprosoft.edd.demo.customer.GetCustomerQuery;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.http.EddServer;
import com.alphaprosoft.edd.http.HttpServiceClient;
import com.alphaprosoft.edd.http.Tls;
import com.alphaprosoft.edd.http.Wire;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** End-to-end: greeter-svc resolves its customer dep from customer-svc over HTTP/2. */
class HttpCrossServiceTest {

  private static EddServer customer;
  private static EddServer greeter;
  private static int customerPort;
  private static int greeterPort;

  @BeforeAll
  static void start() throws IOException {
    customerPort = freePort();
    greeterPort = freePort();

    customer = new EddServer(CustomerApp.build(), customerPort, Tls.serverContext());
    RemoteServiceClient remote =
        new HttpServiceClient(Map.of(CustomerIds.SERVICE, "https://localhost:" + customerPort));
    greeter =
        new EddServer(
            GreeterApp.build(
                remote, InMemoryEventStore.builder().build(), InMemoryViewStore.builder().build()),
            greeterPort,
            Tls.serverContext());

    customer.start();
    greeter.start();
  }

  @AfterAll
  static void stop() {
    if (greeter != null) {
      greeter.stop();
    }
    if (customer != null) {
      customer.stop();
    }
  }

  private static HttpClient h2() {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .sslContext(Tls.trustAllContext())
        .build();
  }

  @Test
  void commandResolvesRemoteDepOverHttp2() throws Exception {
    UUID id = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("https://localhost:" + greeterPort + "/api/command"))
            .version(HttpClient.Version.HTTP_2)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    Wire.MAPPER.writeValueAsString(
                        Map.of(
                            "cmdId",
                            "greet-customer",
                            "command",
                            new GreetCustomerCommand(id, customerId),
                            "meta",
                            Map.of()))))
            .build();

    HttpResponse<String> resp = h2().send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(HttpClient.Version.HTTP_2, resp.version());
    assertEquals(200, resp.statusCode());
    JsonNode root = Wire.MAPPER.readTree(resp.body());
    assertEquals("success", root.get("status").asText());
    assertEquals("Hello, Ada Lovelace!", root.get("events").get(0).get("greeting").asText());

    // the command projected the aggregate into the view store; read it back via a query
    HttpRequest query =
        HttpRequest.newBuilder(URI.create("https://localhost:" + greeterPort + "/api/query"))
            .version(HttpClient.Version.HTTP_2)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    Wire.MAPPER.writeValueAsString(
                        Map.of(
                            "queryId",
                            "get-greeting",
                            "query",
                            new GetGreetingQuery(id),
                            "meta",
                            Map.of()))))
            .build();
    JsonNode result =
        Wire.MAPPER
            .readTree(h2().send(query, HttpResponse.BodyHandlers.ofString()).body())
            .get("result");
    assertEquals("Hello, Ada Lovelace!", result.get("greeting").asText());
    assertEquals(1, result.get("version").asLong());
  }

  @Test
  void queryResolvesRemoteDepOverHttp2() throws Exception {
    // greeter-svc has no customer data; get-customer-name's dep is fetched from customer-svc.
    HttpRequest query =
        HttpRequest.newBuilder(URI.create("https://localhost:" + greeterPort + "/api/query"))
            .version(HttpClient.Version.HTTP_2)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    Wire.MAPPER.writeValueAsString(
                        Map.of(
                            "queryId",
                            "get-customer-name",
                            "query",
                            new GetCustomerNameQuery(UUID.randomUUID()),
                            "meta",
                            Map.of()))))
            .build();

    HttpResponse<String> resp = h2().send(query, HttpResponse.BodyHandlers.ofString());

    assertEquals(HttpClient.Version.HTTP_2, resp.version());
    assertEquals(200, resp.statusCode());
    assertEquals(
        "Ada Lovelace",
        Wire.MAPPER.readTree(resp.body()).get("result").asText(),
        "query dep resolved from customer-svc over HTTP/2");
  }

  @Test
  void topLevelQueryRoutedToOwningService() throws Exception {
    // greeter-svc has no get-customer handler; it declares customer-svc as the owner, so the whole
    // query is forwarded there (top-level routing, not a dep of a local query).
    HttpRequest query =
        HttpRequest.newBuilder(URI.create("https://localhost:" + greeterPort + "/api/query"))
            .version(HttpClient.Version.HTTP_2)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    Wire.MAPPER.writeValueAsString(
                        Map.of(
                            "queryId",
                            "get-customer",
                            "query",
                            new GetCustomerQuery(UUID.randomUUID()),
                            "meta",
                            Map.of()))))
            .build();

    HttpResponse<String> resp = h2().send(query, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, resp.statusCode());
    assertEquals(
        "Ada Lovelace",
        Wire.MAPPER.readTree(resp.body()).get("result").get("name").asText(),
        "inbound query forwarded wholesale to customer-svc");
  }

  @Test
  void http1IsRejected() throws Exception {
    HttpClient http1 =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(Tls.trustAllContext())
            .build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("https://localhost:" + greeterPort + "/api/command"))
            .version(HttpClient.Version.HTTP_1_1)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    HttpResponse<String> resp = http1.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(505, resp.statusCode());
    assertTrue(resp.body().contains("HTTP/2 required"));
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
