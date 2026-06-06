package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.query.Dep;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface Context {

  <T> T getDeps(Dep<?, T> key);

  UUID requestId();

  UUID interactionId();

  String realm();

  User user();

  /** Free-form request annotations (Kubernetes-style metadata). */
  Map<String, String> annotations();

  /** Latest aggregate state from the view store for this realm, if present. */
  <A extends Aggregate> Optional<A> getAggregate(UUID aggregateId);

  /**
   * Historical aggregate state at an exact {@code version} from the view store's version history,
   * if present (edd-core's {@code get-by-id-and-version}). A query that carries a version returns
   * this; a query without one uses {@link #getAggregate(UUID)} (latest). The service is responsible
   * for keeping historical aggregate shapes readable.
   */
  <A extends Aggregate> Optional<A> getAggregate(UUID aggregateId, long version);
}
