package com.alphaprosoft.edd.demo.customer;

import com.alphaprosoft.edd.query.QueryId;

/** The customer-svc contract: service name + query id, referenced by callers. */
public final class CustomerIds {

  public static final String SERVICE = "customer-svc";

  public static final QueryId<GetCustomerQuery, Customer> GET_CUSTOMER =
      QueryId.of("get-customer", GetCustomerQuery.class, Customer.class);

  private CustomerIds() {}
}
