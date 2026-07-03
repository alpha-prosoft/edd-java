package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Context;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link Context} bound to one aggregate type {@code A} — what a module-registered query handler
 * receives. Reads of the module's own aggregate need no type token; other aggregates are read via
 * the inherited {@link Context#getAggregate(UUID, Class)}.
 */
public interface QueryContext<A extends Aggregate> extends Context {

  /** Latest state of this module's aggregate, if present. */
  Optional<A> getAggregate(UUID aggregateId);

  /** This module's aggregate at an exact version, if stored. */
  Optional<A> getAggregate(UUID aggregateId, long version);
}
