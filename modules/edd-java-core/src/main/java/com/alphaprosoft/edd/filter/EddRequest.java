package com.alphaprosoft.edd.filter;

import com.alphaprosoft.edd.core.RequestMeta;
import java.util.HashMap;
import java.util.Map;

/**
 * The unit a {@link Filter} chain processes. Carries the (possibly still-raw) {@code body}, the
 * {@link RequestMeta}, and a free-form {@code attributes} bag for filters to stash data (e.g. an
 * AWS decode filter replaces the raw event body with decoded commands; an idempotency filter
 * records the cache key). Mutable on purpose — a filter chain threads one instance.
 */
public final class EddRequest {

  private Object body;
  private RequestMeta meta;
  private final Map<String, Object> attributes = new HashMap<>();

  public EddRequest(Object body, RequestMeta meta) {
    this.body = body;
    this.meta = meta;
  }

  public Object body() {
    return body;
  }

  public EddRequest body(Object body) {
    this.body = body;
    return this;
  }

  public RequestMeta meta() {
    return meta;
  }

  public EddRequest meta(RequestMeta meta) {
    this.meta = meta;
    return this;
  }

  public Map<String, Object> attributes() {
    return attributes;
  }
}
