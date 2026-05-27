package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link RemoteServiceClient} that routes to other {@link Application}s in the same JVM — the
 * in-memory stand-in for HTTP service-to-service calls.
 */
public final class InProcessServiceRouter implements RemoteServiceClient {

  private final Map<String, Application> services = new HashMap<>();

  public InProcessServiceRouter register(String service, Application app) {
    services.put(service, app);
    return this;
  }

  @Override
  public <Q extends Query, R> R query(String service, QueryId<Q, R> id, Q query, RequestMeta meta) {
    Application app = services.get(service);
    if (app == null) {
      throw new IllegalStateException("No service registered for '" + service + "'");
    }
    return app.query(id, query, meta);
  }
}
