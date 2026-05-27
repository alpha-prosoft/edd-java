package com.alphaprosoft.edd.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Provenance stamped onto every stored event, modelled as a Kubernetes-style {@code annotations}
 * map. Carries the request's own annotations plus the standard provenance keys ({@link
 * #REQUEST_ID}, {@link #INTERACTION_ID}, {@link #REALM}, {@link #USER_ID}, {@link #ROLE}, {@link
 * #CREATED_ON}). Domain event records never carry these fields, so edd-core's reserved-key rule is
 * satisfied by construction.
 */
public record EventMeta(Map<String, String> annotations) {

  public static final String REQUEST_ID = "request-id";
  public static final String INTERACTION_ID = "interaction-id";
  public static final String REALM = "realm";
  public static final String USER_ID = "user-id";
  public static final String ROLE = "role";
  public static final String CREATED_ON = "created-on";

  public EventMeta {
    annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
  }

  public String get(String key) {
    return annotations.get(key);
  }

  public static EventMeta from(RequestMeta meta, Instant createdOn) {
    Map<String, String> a = new LinkedHashMap<>(meta.annotations());
    a.put(REQUEST_ID, meta.requestId().toString());
    a.put(INTERACTION_ID, meta.interactionId().toString());
    a.put(REALM, meta.realm());
    a.put(CREATED_ON, createdOn.toString());
    User user = meta.user();
    if (user != null) {
      if (user.id() != null) {
        a.put(USER_ID, user.id());
      }
      if (user.role() != null) {
        a.put(ROLE, user.role());
      }
    }
    return new EventMeta(a);
  }

  private static final Set<String> STANDARD =
      Set.of(REQUEST_ID, INTERACTION_ID, REALM, USER_ID, ROLE, CREATED_ON);

  /**
   * Reconstruct a {@link RequestMeta} from this event's provenance, so a handler reacting to the
   * event (an effect/fx) sees the user/realm that caused it — meta flows event → ctx.
   */
  public RequestMeta toRequestMeta() {
    RequestMeta.Builder b = RequestMeta.builder();
    if (get(REQUEST_ID) != null) {
      b.requestId(UUID.fromString(get(REQUEST_ID)));
    }
    if (get(INTERACTION_ID) != null) {
      b.interactionId(UUID.fromString(get(INTERACTION_ID)));
    }
    if (get(REALM) != null) {
      b.realm(get(REALM));
    }
    if (get(USER_ID) != null || get(ROLE) != null) {
      b.user(User.of(get(USER_ID), get(ROLE) == null ? "anonymous" : get(ROLE)));
    }
    annotations.forEach(
        (k, v) -> {
          if (!STANDARD.contains(k)) {
            b.annotation(k, v);
          }
        });
    return b.build();
  }
}
