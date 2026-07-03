package com.alphaprosoft.edd.http;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.query.QueryId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;

/**
 * Exposes one {@link Application} over HTTP/2 at {@code POST /api/command} and {@code /api/query}
 * (queries also over {@code GET /api/query?queryId=…&field=…}). HTTP/2 is mandatory for the API: a
 * non-HTTP/2 request to {@code /api/*} is rejected with {@code 505}.
 *
 * <p>The handler chain wraps the routing in cross-cutting HTTP filters: a protocol-independent
 * {@code GET /health} check (so load balancers can probe over HTTP/1.1), CORS (incl. {@code
 * OPTIONS} preflight), and gzip response encoding negotiated from {@code Accept-Encoding}.
 */
public final class EddServer {

  private final Undertow server;

  public EddServer(Application app, int port, SSLContext sslContext) {
    this(app, port, sslContext, "*");
  }

  public EddServer(Application app, int port, SSLContext sslContext, String corsAllowOrigin) {
    HttpHandler routes =
        Handlers.routing()
            .post("/api/command", new CommandHandler(app))
            .post("/api/query", new QueryHandler(app))
            .get("/api/query", new QueryHandler(app));
    HttpHandler gzip =
        new EncodingHandler(
                new ContentEncodingRepository()
                    .addEncodingHandler("gzip", new GzipEncodingProvider(), 50))
            .setNext(routes);
    HttpHandler chain = new Health(new Cors(corsAllowOrigin, new Http2Only(gzip)));
    this.server =
        Undertow.builder()
            .addHttpsListener(port, "0.0.0.0", sslContext)
            .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
            .setHandler(chain)
            .build();
  }

  public void start() {
    server.start();
  }

  public void stop() {
    server.stop();
  }

  /**
   * {@code /health} answered before the protocol gate, so an HTTP/1.x load-balancer probe works.
   */
  private record Health(HttpHandler next) implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      if ("/health".equals(exchange.getRequestPath())) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send("{\"status\":\"ok\"}");
        return;
      }
      next.handleRequest(exchange);
    }
  }

  /**
   * Adds CORS headers to every response and answers {@code OPTIONS} preflight (before the gate).
   */
  private record Cors(String allowOrigin, HttpHandler next) implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      exchange
          .getResponseHeaders()
          .put(new HttpString("Access-Control-Allow-Origin"), allowOrigin)
          .put(new HttpString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS")
          .put(new HttpString("Access-Control-Allow-Headers"), "Content-Type, Authorization");
      if (Methods.OPTIONS.equals(exchange.getRequestMethod())) {
        exchange.setStatusCode(StatusCodes.NO_CONTENT);
        exchange.endExchange();
        return;
      }
      next.handleRequest(exchange);
    }
  }

  /** Rejects anything that is not HTTP/2 — the strict "HTTP/2 only" gate for the API. */
  private record Http2Only(HttpHandler next) implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      if (!Protocols.HTTP_2_0.equals(exchange.getProtocol())) {
        exchange.setStatusCode(StatusCodes.HTTP_VERSION_NOT_SUPPORTED);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange
            .getResponseSender()
            .send("{\"error\":\"HTTP/2 required, got " + exchange.getProtocol() + "\"}");
        return;
      }
      next.handleRequest(exchange);
    }
  }

  /**
   * Reads the full JSON body off a worker thread, delegates, writes the JSON reply. Only envelope
   * problems ({@link BadRequest}: malformed JSON, unknown/missing ids, unbindable payloads, bad
   * meta) answer 400; anything past parsing — including response serialization — is a 500.
   */
  private abstract static class JsonHandler implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      if (exchange.isInIoThread()) {
        exchange.dispatch(this);
        return;
      }
      exchange.startBlocking();
      String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String response;
      try {
        response = handle(exchange, body);
        exchange.setStatusCode(StatusCodes.OK);
      } catch (BadRequest e) {
        exchange.setStatusCode(StatusCodes.BAD_REQUEST);
        response = errorJson(e);
      } catch (Exception e) {
        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
        response = errorJson(e);
      }
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
      exchange.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
    }

    private static String errorJson(Exception e) throws JsonProcessingException {
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      return Wire.MAPPER.writeValueAsString(Map.of("error", message));
    }

    abstract String handle(HttpServerExchange exchange, String body) throws Exception;
  }

  /** An envelope the client got wrong — the only failures mapped to 400. */
  private static final class BadRequest extends RuntimeException {
    BadRequest(String message) {
      super(message);
    }

    BadRequest(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static JsonNode parseBody(String body) {
    try {
      return Wire.MAPPER.readTree(body);
    } catch (JsonProcessingException e) {
      throw new BadRequest("Malformed JSON: " + e.getOriginalMessage(), e);
    }
  }

  private static JsonNode required(JsonNode root, String field) {
    JsonNode node = root.get(field);
    if (node == null || node.isNull()) {
      throw new BadRequest("Missing field: " + field);
    }
    return node;
  }

  private static <T> T bound(JsonNode node, Class<T> type, String field) {
    try {
      return Wire.MAPPER.convertValue(node, type);
    } catch (IllegalArgumentException e) {
      throw new BadRequest("Invalid " + field + ": " + e.getMessage(), e);
    }
  }

  private static RequestMeta parsedMeta(JsonNode root) {
    try {
      return Wire.parseMeta(root.get("meta"));
    } catch (IllegalArgumentException e) {
      throw new BadRequest("Invalid meta: " + e.getMessage(), e);
    }
  }

  private record CommandHandler(Application app) implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      new JsonHandler() {
        @Override
        String handle(HttpServerExchange ex, String body) throws Exception {
          JsonNode root = parseBody(body);
          String cmdId = required(root, "cmdId").asText();
          CommandId<?> id =
              CommandId.lookup(cmdId).orElseThrow(() -> new BadRequest("Unknown cmdId: " + cmdId));
          Command cmd = bound(required(root, "command"), id.type(), "command");
          RequestMeta meta = parsedMeta(root);
          return Wire.MAPPER.writeValueAsString(responseJson(app.dispatch(cmd, meta)));
        }
      }.handleRequest(exchange);
    }
  }

  private record QueryHandler(Application app) implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      new JsonHandler() {
        @Override
        String handle(HttpServerExchange ex, String body) throws Exception {
          JsonNode root =
              Methods.GET.equals(ex.getRequestMethod()) ? fromQueryString(ex) : parseBody(body);
          String queryId = required(root, "queryId").asText();
          QueryId<?, ?> id =
              QueryId.lookup(queryId)
                  .orElseThrow(() -> new BadRequest("Unknown queryId: " + queryId));
          RequestMeta meta = parsedMeta(root);
          JsonNode queryNode = required(root, "query");
          Object result = app.queryDecoded(id, type -> bound(queryNode, type, "query"), meta);
          // a query may legitimately return null (aggregate not found); Map.of rejects null
          return Wire.MAPPER.writeValueAsString(Collections.singletonMap("result", result));
        }
      }.handleRequest(exchange);
    }
  }

  /**
   * Build the {@code {queryId, query, meta}} envelope from {@code ?queryId=…&field=…} parameters.
   */
  private static JsonNode fromQueryString(HttpServerExchange exchange) {
    ObjectNode root = Wire.MAPPER.createObjectNode();
    ObjectNode query = Wire.MAPPER.createObjectNode();
    exchange
        .getQueryParameters()
        .forEach(
            (key, values) -> {
              if (values.isEmpty()) {
                return;
              }
              if ("queryId".equals(key)) {
                root.put("queryId", values.getFirst());
              } else {
                query.put(key, values.getFirst());
              }
            });
    root.set("query", query);
    return root;
  }

  private static Map<String, Object> responseJson(CommandResponse resp) {
    Map<String, Object> out = new LinkedHashMap<>();
    switch (resp) {
      case CommandResponse.Success s -> {
        out.put("status", "success");
        out.put("aggregateId", s.aggregateId());
        out.put("events", s.events());
        out.put("identities", s.identities());
        out.put("effects", s.effects());
      }
      case CommandResponse.Failure f -> {
        out.put("status", "failure");
        out.put("code", f.code());
        out.put("details", f.details());
      }
    }
    return out;
  }
}
