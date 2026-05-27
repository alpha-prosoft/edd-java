package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.core.RemoteServiceClient;
import java.util.Objects;

/**
 * A typed key into the resolved context: fetch a {@code T} by running query {@code Q}. When {@code
 * service} is non-null and differs from the running service, the query is resolved
 * <em>remotely</em> through the configured {@link RemoteServiceClient} (edd-core's {@code :service}
 * dep); otherwise it runs locally.
 */
public record Dep<Q extends Query, T>(String name, String service, QueryId<Q, T> queryId) {

  public Dep {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(queryId, "queryId");
  }

  /** A local dependency — the query is owned by the running service. */
  public static <Q extends Query, T> Dep<Q, T> of(String name, QueryId<Q, T> queryId) {
    return new Dep<>(name, null, queryId);
  }

  /** A remote dependency — the query is owned by {@code service} and resolved over the wire. */
  public static <Q extends Query, T> Dep<Q, T> remote(
      String name, String service, QueryId<Q, T> queryId) {
    Objects.requireNonNull(service, "service");
    return new Dep<>(name, service, queryId);
  }

  public boolean isRemote() {
    return service != null;
  }

  @Override
  public String toString() {
    return "Dep[" + name + (service == null ? "" : "@" + service) + " -> " + queryId + "]";
  }
}
