package com.alphaprosoft.edd.viewstore.postgres;

import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.store.compliance.ViewStoreCompliance;
import org.junit.jupiter.api.BeforeAll;

/**
 * Runs the shared {@link ViewStoreCompliance} suite against Postgres (docker-compose service),
 * built through {@link PostgresViewStore#fromConfig} so the unified config path is exercised live.
 */
class PostgresViewStoreComplianceTest extends ViewStoreCompliance {

  private static PostgresViewStore store;

  @BeforeAll
  static void connect() {
    try {
      store = PostgresViewStore.builder().config(Config.load()).build();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Postgres not reachable — this compliance suite must run against Postgres. "
              + "Start it via `docker compose up -d postgres` before building; the suite must "
              + "not be skipped.",
          e);
    }
  }

  @Override
  protected ViewStore newStore() {
    return store;
  }

  @Override
  protected ViewStore newStore(String service) {
    return PostgresViewStore.builder().config(Config.load()).service(service).build();
  }
}
