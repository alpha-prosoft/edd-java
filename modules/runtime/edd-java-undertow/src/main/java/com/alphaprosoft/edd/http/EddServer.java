package com.alphaprosoft.edd.http;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
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

  /** Reads the full JSON body off a worker thread, delegates, writes the JSON reply. */
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
      } catch (Exception e) {
        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
        response = Wire.MAPPER.writeValueAsString(Map.of("error", String.valueOf(e.getMessage())));
      }
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
      exchange.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
    }

    abstract String handle(HttpServerExchange exchange, String body) throws Exception;
  }

  private record CommandHandler(Application app) implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      new JsonHandler() {
        @Override
        String handle(HttpServerExchange ex, String body) throws Exception {
          JsonNode root = Wire.MAPPER.readTree(body);
          String cmdId = root.get("cmdId").asText();
          CommandId<?> id =
              CommandId.lookup(cmdId)
                  .orElseThrow(() -> new IllegalArgumentException("Unknown cmdId: " + cmdId));
          Command cmd = (Command) Wire.MAPPER.convertValue(root.get("command"), id.type());
          RequestMeta meta = Wire.parseMeta(root.get("meta"));
          return Wire.MAPPER.writeValueAsString(responseJson(app.dispatch(cmd, meta)));
        }
      }.handleRequest(exchange);
    }
  }

  private record QueryHandler(Application app) implements HttpHandler {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      new JsonHandler() {
        @Override
        String handle(HttpServerExchange ex, String body) throws Exception {
          JsonNode root =
              Methods.GET.equals(ex.getRequestMethod())
                  ? fromQueryString(ex)
                  : Wire.MAPPER.readTree(body);
          String queryId = root.get("queryId").asText();
          QueryId<?, ?> id =
              QueryId.lookup(queryId)
                  .orElseThrow(() -> new IllegalArgumentException("Unknown queryId: " + queryId));
          Query query = (Query) Wire.MAPPER.convertValue(root.get("query"), id.queryType());
          RequestMeta meta = Wire.parseMeta(root.get("meta"));
          Object result = app.query((QueryId) id, query, meta);
          return Wire.MAPPER.writeValueAsString(Map.of("result", result));
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
