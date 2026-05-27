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

  /** Current aggregate state from the view store for this realm, if present. */
  <A extends Aggregate> Optional<A> getAggregate(UUID aggregateId);
}
