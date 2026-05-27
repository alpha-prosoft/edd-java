package com.alphaprosoft.edd.crossservice;

import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.order.Customer;
import com.alphaprosoft.edd.order.OrderModule;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.query.Dep;
import com.alphaprosoft.edd.query.QueryId;

public final class GreeterRegistry {

  public static final String CUSTOMER_SERVICE = "customer-svc";

  public static final CommandId<GreetCommand> GREET = CommandId.of("greet", GreetCommand.class);

  public static final EventId<GreetedEvent> GREETED = EventId.of("greeted", GreetedEvent.class);

  public static final QueryId<CustomerNameQuery, String> CUSTOMER_NAME =
      QueryId.of("customer-name", CustomerNameQuery.class, String.class);

  /** Customer lives in another service — resolved remotely. */
  public static final Dep<GetCustomerQuery, Customer> CUSTOMER =
      Dep.remote("customer", CUSTOMER_SERVICE, OrderModule.GET_CUSTOMER);

  private GreeterRegistry() {}
}
