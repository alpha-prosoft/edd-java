package com.example.sample;

import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.eventstore.postgres.PostgresEventStore;
import com.alphaprosoft.edd.viewstore.postgres.PostgresViewStore;

/**
 * The wired {@link Application} for the Postgres backend. Connection details are read from {@code
 * Config} (defaults &rarr; {@code edd.properties} &rarr; {@code -Dedd.*} &rarr; {@code EDD_*}, e.g.
 * {@code EDD_STORE_URL} / {@code EDD_STORE_USER} / {@code EDD_STORE_PASSWORD}).
 */
public final class SampleApp {

  public static Application build() {
    return Application.builder(SampleIds.SERVICE)
        .config(Config.load())
        .eventStore(PostgresEventStore::fromConfig)
        .viewStore(PostgresViewStore::fromConfig)
        .module(SampleModule::register)
        .build();
  }

  /** Same wiring over caller-supplied stores — used by tests to run offline on in-memory stores. */
  public static Application build(EventStore eventStore, ViewStore viewStore) {
    return Application.builder(SampleIds.SERVICE)
        .eventStore(eventStore)
        .viewStore(viewStore)
        .module(SampleModule::register)
        .build();
  }

  private SampleApp() {}
}
