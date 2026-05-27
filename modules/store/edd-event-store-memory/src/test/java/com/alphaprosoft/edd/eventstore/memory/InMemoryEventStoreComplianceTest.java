package com.alphaprosoft.edd.eventstore.memory;

import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.store.compliance.EventStoreCompliance;

class InMemoryEventStoreComplianceTest extends EventStoreCompliance {

  @Override
  protected EventStore newStore() {
    return InMemoryEventStore.builder().build();
  }
}
