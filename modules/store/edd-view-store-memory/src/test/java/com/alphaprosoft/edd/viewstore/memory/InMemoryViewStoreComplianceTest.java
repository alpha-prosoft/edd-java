package com.alphaprosoft.edd.viewstore.memory;

import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.store.compliance.ViewStoreCompliance;

class InMemoryViewStoreComplianceTest extends ViewStoreCompliance {

  @Override
  protected ViewStore newStore() {
    return InMemoryViewStore.builder().build();
  }
}
