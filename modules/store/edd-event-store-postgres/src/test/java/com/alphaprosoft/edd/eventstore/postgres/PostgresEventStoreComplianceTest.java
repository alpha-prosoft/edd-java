package com.alphaprosoft.edd.eventstore.postgres;

import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.store.compliance.EventStoreCompliance;
import org.junit.jupiter.api.BeforeAll;

/**
 * Runs the shared {@link EventStoreCompliance} suite against Postgres (docker-compose service),
 * built through the config-driven builder so the unified config path is exercised live.
 */
class PostgresEventStoreComplianceTest extends EventStoreCompliance {

  private static PostgresEventStore store;

  @BeforeAll
  static void connect() {
    try {
      // defaults to localhost:5432/edd; override with EDD_STORE_URL / EDD_STORE_USER /
      // EDD_STORE_PASSWORD
      store = PostgresEventStore.builder().config(Config.load()).build();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Postgres not reachable — this compliance suite must run against Postgres. "
              + "Start it via `docker compose up -d postgres` before building; the suite must "
              + "not be skipped.",
          e);
    }
  }

  @Override
  protected EventStore newStore() {
    return store;
  }
}
