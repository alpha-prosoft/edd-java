package com.alphaprosoft.edd.demo.customer;

import com.alphaprosoft.edd.core.Application;

public final class CustomerApp {

  public static Application build() {
    return Application.builder(CustomerIds.SERVICE)
        .regQuery(CustomerIds.GET_CUSTOMER, (ctx, q) -> new Customer(q.id(), "Ada Lovelace"))
        .build();
  }

  private CustomerApp() {}
}
