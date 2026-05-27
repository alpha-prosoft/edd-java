package com.alphaprosoft.edd.eventstore.dynamodb;

import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.store.compliance.EventStoreCompliance;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;

/**
 * Runs the shared {@link EventStoreCompliance} suite against real DynamoDB (dev01), built through
 * the config-driven builder so the unified config path is exercised live.
 */
class DynamoDbEventStoreComplianceTest extends EventStoreCompliance {

  private static DynamoDbEventStore store;

  @BeforeAll
  static void connect() {
    Config config =
        Config.builder()
            .defaults(Map.of("store.table-prefix", "edd-java-test", "store.region", "eu-west-1"))
            .fromEnvironment()
            .build();
    try {
      store = DynamoDbEventStore.builder().config(config).build();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "DynamoDB not reachable — this compliance suite must run against live DynamoDB (dev01). "
              + "Authenticate (aws login) and bridge credentials before building; the suite must "
              + "not be skipped.",
          e);
    }
  }

  @Override
  protected EventStore newStore() {
    return store;
  }
}
