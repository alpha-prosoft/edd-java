package com.alphaprosoft.edd.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.core.User;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ContextMetaAndQueryDepsTest {

  @Test
  void contextExposesRealmAndUser() {
    AtomicReference<String> seenRealm = new AtomicReference<>();
    AtomicReference<User> seenUser = new AtomicReference<>();

    Application app =
        Application.builder("order-svc")
            .regQuery(
                OrderModule.GET_ORDER,
                (ctx, q) -> {
                  seenRealm.set(ctx.realm());
                  seenUser.set(ctx.user());
                  return null;
                })
            .build();

    User alice = User.of("alice@example.com", "order-admin");
    RequestMeta meta = RequestMeta.builder().realm("tenant-a").user(alice).build();

    app.query(OrderModule.GET_ORDER, new GetOrderQuery(UUID.randomUUID()), meta);

    assertEquals("tenant-a", seenRealm.get());
    assertSame(alice, seenUser.get());
    assertEquals("order-admin", seenUser.get().role());
  }

  @Test
  void defaultRequestMetaIsTestRealmAndAnonymous() {
    AtomicReference<String> seenRealm = new AtomicReference<>();
    AtomicReference<User> seenUser = new AtomicReference<>();

    Application app =
        Application.builder("order-svc")
            .regQuery(
                OrderModule.GET_ORDER,
                (ctx, q) -> {
                  seenRealm.set(ctx.realm());
                  seenUser.set(ctx.user());
                  return null;
                })
            .build();

    app.query(
        OrderModule.GET_ORDER, new GetOrderQuery(UUID.randomUUID()), RequestMeta.newRequest());

    assertEquals(RequestMeta.DEFAULT_REALM, seenRealm.get());
    assertSame(User.ANONYMOUS, seenUser.get());
  }

  @Test
  void queryResolvesItsOwnDeps() {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Customer customer = new Customer(customerId, "Alice", Customer.Tier.GOLD);

    // GET_ORDER declares a dep on GET_CUSTOMER and reads it from ctx,
    // proving query-level deps are resolved before the handler runs.
    Application app =
        Application.builder("order-svc")
            .regQuery(OrderModule.GET_CUSTOMER, (ctx, q) -> customer)
            .regQuery(
                OrderModule.GET_ORDER,
                spec ->
                    spec.handler(
                            (ctx, q) -> {
                              Customer dep = ctx.getDeps(OrderDeps.CUSTOMER);
                              return new OrderAggregate(
                                  q.id(),
                                  1,
                                  OrderStatus.PLACED,
                                  dep.id(),
                                  UUID.randomUUID(),
                                  1,
                                  Money.usd(100),
                                  null);
                            })
                        .dep(
                            OrderDeps.CUSTOMER,
                            (ctx, q) -> GetCustomerQuery.builder().id(customerId).build())
                        .build())
            .build();

    OrderAggregate result =
        app.query(OrderModule.GET_ORDER, new GetOrderQuery(orderId), RequestMeta.newRequest());

    assertEquals(orderId, result.id());
    assertEquals(
        customerId,
        result.customerId(),
        "customer dep should be resolved and visible to the query handler");
  }
}
