package com.alphaprosoft.edd.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-request envelope. Beyond the correlation ids it carries the tenant {@code realm} (default
 * {@value #DEFAULT_REALM}), the acting {@link User} (default {@link User#ANONYMOUS}), a free-form
 * {@code annotations} map (Kubernetes-style), and {@code breadcrumbs} — the causal path of a
 * request (root {@code [0]}), the second half of the idempotency key (with {@code requestId}). All
 * are surfaced to handlers via {@link Context}.
 */
public record RequestMeta(
    UUID requestId,
    UUID interactionId,
    String realm,
    User user,
    Map<String, String> annotations,
    List<Integer> breadcrumbs) {

  public static final String DEFAULT_REALM = "test";
  public static final List<Integer> ROOT_BREADCRUMB = List.of(0);

  public RequestMeta {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(interactionId, "interactionId");
    realm = realm == null ? DEFAULT_REALM : realm;
    user = user == null ? User.ANONYMOUS : user;
    annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    breadcrumbs =
        breadcrumbs == null || breadcrumbs.isEmpty() ? ROOT_BREADCRUMB : List.copyOf(breadcrumbs);
  }

  public static RequestMeta newRequest() {
    return new RequestMeta(
        UUID.randomUUID(),
        UUID.randomUUID(),
        DEFAULT_REALM,
        User.ANONYMOUS,
        Map.of(),
        ROOT_BREADCRUMB);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(RequestMeta existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID requestId = UUID.randomUUID();
    private UUID interactionId = UUID.randomUUID();
    private String realm = DEFAULT_REALM;
    private User user = User.ANONYMOUS;
    private final Map<String, String> annotations = new LinkedHashMap<>();
    private List<Integer> breadcrumbs = ROOT_BREADCRUMB;

    private Builder() {}

    private Builder(RequestMeta m) {
      this.requestId = m.requestId;
      this.interactionId = m.interactionId;
      this.realm = m.realm;
      this.user = m.user;
      this.annotations.putAll(m.annotations);
      this.breadcrumbs = m.breadcrumbs;
    }

    public Builder requestId(UUID requestId) {
      this.requestId = requestId;
      return this;
    }

    public Builder interactionId(UUID interactionId) {
      this.interactionId = interactionId;
      return this;
    }

    public Builder realm(String realm) {
      this.realm = realm;
      return this;
    }

    public Builder user(User user) {
      this.user = user;
      return this;
    }

    public Builder annotation(String key, String value) {
      this.annotations.put(key, value);
      return this;
    }

    public Builder annotations(Map<String, String> annotations) {
      this.annotations.putAll(annotations);
      return this;
    }

    public Builder breadcrumbs(List<Integer> breadcrumbs) {
      this.breadcrumbs = breadcrumbs;
      return this;
    }

    public RequestMeta build() {
      return new RequestMeta(requestId, interactionId, realm, user, annotations, breadcrumbs);
    }
  }
}
