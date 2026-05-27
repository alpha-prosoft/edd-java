package com.alphaprosoft.edd.crossservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.InProcessServiceRouter;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.order.Customer;
import com.alphaprosoft.edd.order.OrderModule;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossServiceTest {

  private static Application customerService() {
    return Application.builder(GreeterRegistry.CUSTOMER_SERVICE)
        .regQuery(
            OrderModule.GET_CUSTOMER,
            (ctx, q) -> new Customer(q.id(), "Remote Alice", Customer.Tier.GOLD))
        .build();
  }

  private static Application greeterService(InProcessServiceRouter router) {
    return Application.builder("greeter-svc")
        .remoteClient(router)
        .eventStore(InMemoryEventStore.builder().build())
        .viewStore(InMemoryViewStore.builder().build())
        .module(
            Module.builder(GreeterAggregate.class)
                .regCmd(
                    GreeterRegistry.GREET,
                    spec ->
                        spec.handler(GreetHandler.class)
                            .dep(
                                GreeterRegistry.CUSTOMER,
                                (ctx, cmd) -> new GetCustomerQuery(cmd.customerId()))
                            .build())
                .regApply(GreeterRegistry.GREETED, GreeterAggregate::greeted)
                .regQuery(
                    GreeterRegistry.CUSTOMER_NAME,
                    spec ->
                        spec.handler((ctx, q) -> ctx.getDeps(GreeterRegistry.CUSTOMER).name())
                            .dep(
                                GreeterRegistry.CUSTOMER,
                                (ctx, q) -> new GetCustomerQuery(q.customerId()))
                            .build())
                .build())
        .build();
  }

  @Test
  void queryResolvesRemoteDepAgainstAnotherService() {
    InProcessServiceRouter router =
        new InProcessServiceRouter().register(GreeterRegistry.CUSTOMER_SERVICE, customerService());
    Application greeter = greeterService(router);

    String name =
        greeter.query(
            GreeterRegistry.CUSTOMER_NAME,
            new CustomerNameQuery(UUID.randomUUID()),
            RequestMeta.newRequest());

    assertEquals("Remote Alice", name);
  }

  @Test
  void remoteDepResolvesAgainstAnotherService() {
    InProcessServiceRouter router =
        new InProcessServiceRouter().register(GreeterRegistry.CUSTOMER_SERVICE, customerService());
    Application greeter = greeterService(router);

    CommandResponse resp =
        greeter.dispatch(
            new GreetCommand(UUID.randomUUID(), UUID.randomUUID()), RequestMeta.newRequest());

    var success = assertInstanceOf(CommandResponse.Success.class, resp);
    GreetedEvent greeted = assertInstanceOf(GreetedEvent.class, success.events().getFirst());
    assertEquals("Hello Remote Alice", greeted.greeting());
  }

  @Test
  void remoteDepWithoutClientFailsFast() {
    // No remoteClient configured — the remote dep cannot be resolved.
    Application greeter =
        Application.builder("greeter-svc")
            .eventStore(InMemoryEventStore.builder().build())
            .viewStore(InMemoryViewStore.builder().build())
            .module(
                Module.builder(GreeterAggregate.class)
                    .regCmd(
                        GreeterRegistry.GREET,
                        spec ->
                            spec.handler(GreetHandler.class)
                                .dep(
                                    GreeterRegistry.CUSTOMER,
                                    (ctx, cmd) -> new GetCustomerQuery(cmd.customerId()))
                                .build())
                    .regApply(GreeterRegistry.GREETED, GreeterAggregate::greeted)
                    .build())
            .build();

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                greeter.dispatch(
                    new GreetCommand(UUID.randomUUID(), UUID.randomUUID()),
                    RequestMeta.newRequest()));
    assertEquals(true, ex.getMessage().contains("customer-svc"));
  }
}
