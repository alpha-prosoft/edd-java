package com.alphaprosoft.edd.http;

import com.alphaprosoft.edd.core.RemoteServiceClient;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Resolves remote query deps by calling another service's {@code POST /api/query} over HTTP/2.
 * Refuses anything but an HTTP/2 response, so a peer that silently downgraded would be caught.
 *
 * <p>The base URL is resolved per call from {@code (service, realm)}, so a deployment can route the
 * same logical service to a realm-specific host. If {@code meta.annotations()} carries an {@value
 * #AUTHORIZATION} entry it is forwarded as the {@code Authorization} header, so a verified caller's
 * credentials flow downstream. Transient {@link IOException}s are retried with linear backoff.
 */
public final class HttpServiceClient implements RemoteServiceClient {

  public static final String AUTHORIZATION = "authorization";

  private final BiFunction<String, String, String> urlResolver;
  private final int maxRetries;
  private final Duration requestTimeout;
  private final boolean requireHttp2;
  private final HttpClient http;

  public HttpServiceClient(Map<String, String> baseUrls) {
    this(builder().baseUrls(baseUrls));
  }

  private HttpServiceClient(Builder b) {
    this.urlResolver = Objects.requireNonNull(b.urlResolver, "urlResolver or baseUrls");
    this.maxRetries = b.maxRetries;
    this.requestTimeout = b.requestTimeout;
    this.requireHttp2 = b.requireHttp2;
    this.http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(Tls.trustAllContext())
            .connectTimeout(b.connectTimeout)
            .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public <Q extends Query, R> R query(String service, QueryId<Q, R> id, Q query, RequestMeta meta) {
    String base = urlResolver.apply(service, meta.realm());
    if (base == null) {
      throw new IllegalStateException(
          "No base URL for service '" + service + "' (realm '" + meta.realm() + "')");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("queryId", id.id());
    payload.put("query", query);
    payload.put("meta", Wire.metaJson(meta));
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(base + "/api/query"))
              .version(HttpClient.Version.HTTP_2)
              .timeout(requestTimeout)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(Wire.MAPPER.writeValueAsString(payload)));
      String authorization = meta.annotations().get(AUTHORIZATION);
      if (authorization != null && !authorization.isBlank()) {
        request.header("Authorization", authorization);
      }
      HttpResponse<String> response = sendWithRetry(request.build(), service);
      if (requireHttp2 && response.version() != HttpClient.Version.HTTP_2) {
        throw new IllegalStateException(
            "Expected HTTP/2 from '" + service + "' but got " + response.version());
      }
      JsonNode root = Wire.MAPPER.readTree(response.body());
      if (root.hasNonNull("error")) {
        throw new IllegalStateException(
            "Remote query '"
                + id.id()
                + "' on '"
                + service
                + "' failed: "
                + root.get("error").asText());
      }
      return Wire.MAPPER.convertValue(root.get("result"), id.responseType());
    } catch (IOException e) {
      throw new IllegalStateException("Remote query to '" + service + "' failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted calling '" + service + "'", e);
    }
  }

  private HttpResponse<String> sendWithRetry(HttpRequest request, String service)
      throws IOException, InterruptedException {
    IOException last = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (IOException e) {
        last = e;
        if (attempt < maxRetries) {
          Thread.sleep(Duration.ofMillis(100L * (attempt + 1)).toMillis());
        }
      }
    }
    throw last;
  }

  public static final class Builder {
    private BiFunction<String, String, String> urlResolver;
    private int maxRetries = 2;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private boolean requireHttp2 = true;

    private Builder() {}

    /** Static map of service name to base URL (realm-agnostic). */
    public Builder baseUrls(Map<String, String> baseUrls) {
      Map<String, String> copy = Map.copyOf(baseUrls);
      this.urlResolver = (service, realm) -> copy.get(service);
      return this;
    }

    /** Resolve the base URL from the service name and the request's realm. */
    public Builder urlResolver(BiFunction<String, String, String> urlResolver) {
      this.urlResolver = urlResolver;
      return this;
    }

    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public Builder connectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    public Builder requestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    /**
     * Require the response to negotiate HTTP/2 (default {@code true}). Set {@code false} when the
     * peer is fronted by something that may answer over HTTP/1.1 (e.g. REST API Gateway).
     */
    public Builder requireHttp2(boolean requireHttp2) {
      this.requireHttp2 = requireHttp2;
      return this;
    }

    public HttpServiceClient build() {
      return new HttpServiceClient(this);
    }
  }
}
