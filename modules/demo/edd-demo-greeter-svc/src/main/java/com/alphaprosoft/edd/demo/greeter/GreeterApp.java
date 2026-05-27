package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RemoteServiceClient;
import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.demo.customer.CustomerIds;
import com.alphaprosoft.edd.demo.customer.GetCustomerQuery;

public final class GreeterApp {

  public static Application build(
      RemoteServiceClient remote, EventStore eventStore, ViewStore viewStore) {
    return Application.builder(GreeterIds.SERVICE)
        .remoteClient(remote)
        .eventStore(eventStore)
        .viewStore(viewStore)
        .module(
            Module.builder(GreeterAggregate.class)
                .regCmd(
                    GreeterIds.GREET_CUSTOMER,
                    spec ->
                        spec.handler(GreetCustomerHandler.class)
                            .dep(
                                GreeterIds.CUSTOMER,
                                (ctx, cmd) -> new GetCustomerQuery(cmd.customerId()))
                            .build())
                .regApply(GreeterIds.CUSTOMER_GREETED, GreeterAggregate::greeted)
                .regQuery(
                    GreeterIds.GET_GREETING,
                    (ctx, q) -> ctx.<GreeterAggregate>getAggregate(q.id()).orElse(null))
                // a query whose dep is resolved from customer-svc over the wire
                .regQuery(
                    GreeterIds.GET_CUSTOMER_NAME,
                    spec ->
                        spec.handler((ctx, q) -> ctx.getDeps(GreeterIds.CUSTOMER).name())
                            .dep(
                                GreeterIds.CUSTOMER,
                                (ctx, q) -> new GetCustomerQuery(q.customerId()))
                            .build())
                .build())
        // top-level routing: greeter-svc owns no get-customer handler, so an inbound
        // get-customer query is forwarded wholesale to customer-svc over the wire
        .regRemoteQuery(CustomerIds.GET_CUSTOMER, CustomerIds.SERVICE)
        .build();
  }

  private GreeterApp() {}
}
