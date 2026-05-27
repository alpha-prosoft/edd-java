package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.demo.customer.Customer;
import com.alphaprosoft.edd.demo.customer.CustomerIds;
import com.alphaprosoft.edd.demo.customer.GetCustomerQuery;
import com.alphaprosoft.edd.query.Dep;
import com.alphaprosoft.edd.query.QueryId;

public final class GreeterIds {

  public static final String SERVICE = "greeter-svc";

  public static final CommandId<GreetCustomerCommand> GREET_CUSTOMER =
      CommandId.of("greet-customer", GreetCustomerCommand.class);

  public static final EventId<CustomerGreetedEvent> CUSTOMER_GREETED =
      EventId.of("customer-greeted", CustomerGreetedEvent.class);

  public static final QueryId<GetGreetingQuery, GreeterAggregate> GET_GREETING =
      QueryId.of("get-greeting", GetGreetingQuery.class, GreeterAggregate.class);

  public static final QueryId<GetCustomerNameQuery, String> GET_CUSTOMER_NAME =
      QueryId.of("get-customer-name", GetCustomerNameQuery.class, String.class);

  /** Resolved remotely from customer-svc, using that service's published contract. */
  public static final Dep<GetCustomerQuery, Customer> CUSTOMER =
      Dep.remote("customer", CustomerIds.SERVICE, CustomerIds.GET_CUSTOMER);

  private GreeterIds() {}
}
