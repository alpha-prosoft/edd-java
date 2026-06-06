package com.example.sample;

import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.eventstore.dynamodb.DynamoDbEventStore;
import com.alphaprosoft.edd.viewstore.s3.S3ViewStore;

/**
 * The wired {@link Application} for the AWS backend. Connection details are read from {@code Config}
 * (defaults &rarr; {@code edd.properties} &rarr; {@code -Dedd.*} &rarr; {@code EDD_*}, e.g. {@code
 * EDD_STORE_REGION} / {@code EDD_STORE_TABLEPREFIX} / {@code EDD_STORE_BUCKET}).
 */
public final class SampleApp {

  public static Application build() {
    return Application.builder(SampleIds.SERVICE)
        .config(Config.load())
        .eventStore(DynamoDbEventStore::fromConfig)
        .viewStore(S3ViewStore::fromConfig)
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
