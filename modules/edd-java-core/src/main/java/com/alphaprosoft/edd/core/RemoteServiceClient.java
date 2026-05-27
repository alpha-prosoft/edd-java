package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;

/**
 * Resolves a query owned by another service. edd-core does this over HTTP; the default {@link
 * InProcessServiceRouter} dispatches to another in-process {@link Application}, which is enough for
 * tests and local multi-service wiring.
 */
@FunctionalInterface
public interface RemoteServiceClient {
  <Q extends Query, R> R query(String service, QueryId<Q, R> id, Q query, RequestMeta meta);
}
