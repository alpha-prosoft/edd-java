package com.alphaprosoft.edd.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.alphaprosoft.edd.Application;
import com.alphaprosoft.edd.CommandResponse;
import com.alphaprosoft.edd.Query;
import com.alphaprosoft.edd.QueryHandler;
import com.alphaprosoft.edd.RemoteResolver;
import com.alphaprosoft.edd.RequestMeta;
import com.alphaprosoft.edd.order.command.CancelOrder;
import com.alphaprosoft.edd.order.command.ConfirmPayment;
import com.alphaprosoft.edd.order.command.NotifyCustomer;
import com.alphaprosoft.edd.order.command.PlaceOrder;
import com.alphaprosoft.edd.order.command.ShipOrder;
import com.alphaprosoft.edd.order.event.OrderCancelled;
import com.alphaprosoft.edd.order.event.OrderPlaced;
import com.alphaprosoft.edd.order.event.OrderShipped;
import com.alphaprosoft.edd.order.event.PaymentConfirmed;
import com.alphaprosoft.edd.order.query.GetCustomer;
import com.alphaprosoft.edd.order.query.GetOrder;
import com.alphaprosoft.edd.order.query.GetProduct;

class OrderModuleTest {

    private static Application build(QueryHandler<GetOrder, OrderAggregate> getOrder, RemoteResolver remote) {
        return OrderModule.register(Application.builder("order-svc"))
                .regQuery(OrderIds.GET_ORDER, getOrder)
                .remoteResolver(remote)
                .build();
    }

    private static RemoteResolver stubbedRemotes(Map<Class<? extends Query>, Object> answers) {
        return (_, q) -> {
            Object resp = answers.get(q.getClass());
            if (resp == null) {
                throw new IllegalStateException("No stub for " + q.getClass());
            }
            return resp;
        };
    }

    @Test
    void modulesRegistersWithoutErrors() {
        Application app = build((_, _) -> null, (_, _) -> null);
        assertEquals("order-svc", app.serviceName());
    }

    @Test
    void placeOrderHappyPath() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Customer customer = new Customer(customerId, "Alice", Customer.Tier.GOLD);
        Product product = new Product(productId, "Widget", Money.usd(1000), 50);

        Map<Class<? extends Query>, Object> stubs = new HashMap<>();
        stubs.put(GetCustomer.class, customer);
        stubs.put(GetProduct.class, product);
        Application app = build((_, _) -> null, stubbedRemotes(stubs));

        UUID cmdId = UUID.randomUUID();
        CommandResponse resp = app.dispatch(new PlaceOrder(cmdId, customerId, productId, 3), RequestMeta.newRequest());

        var success = assertInstanceOf(CommandResponse.Success.class, resp);
        assertEquals(1, success.events().size());
        var placed = assertInstanceOf(OrderPlaced.class, success.events().get(0));
        assertEquals(cmdId, placed.id());
        assertEquals(3, placed.quantity());
        assertEquals(3000, placed.total().amountCents());

        assertEquals(1, success.effects().size());
        var fx = success.effects().get(0);
        assertTrue(fx.service().isPresent());
        assertEquals("notification-svc", fx.service().get().name());
        assertInstanceOf(NotifyCustomer.class, fx.command());
    }

    @Test
    void placeOrderRejectsLowStock() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Map<Class<? extends Query>, Object> stubs = new HashMap<>();
        stubs.put(GetCustomer.class, new Customer(customerId, "Bob", Customer.Tier.STANDARD));
        stubs.put(GetProduct.class, new Product(productId, "Scarce", Money.usd(500), 1));
        Application app = build((_, _) -> null, stubbedRemotes(stubs));

        CommandResponse resp =
                app.dispatch(new PlaceOrder(UUID.randomUUID(), customerId, productId, 5), RequestMeta.newRequest());

        var failure = assertInstanceOf(CommandResponse.Failure.class, resp);
        assertEquals("insufficient-stock", failure.code());
    }

    @Test
    void confirmPaymentUsesIdFnAndProducesShipFx() {
        UUID orderId = UUID.randomUUID();
        OrderAggregate placed = new OrderAggregate(
                orderId, 1, OrderStatus.PLACED, UUID.randomUUID(), UUID.randomUUID(), 2, Money.usd(2000), null);

        Application app = build((_, _) -> placed, (_, _) -> {
            throw new IllegalStateException("no remote calls expected");
        });

        CommandResponse resp =
                app.dispatch(new ConfirmPayment(UUID.randomUUID(), orderId, Money.usd(2000)), RequestMeta.newRequest());

        var success = assertInstanceOf(CommandResponse.Success.class, resp);
        var confirmed =
                assertInstanceOf(PaymentConfirmed.class, success.events().get(0));
        assertEquals(orderId, confirmed.id());

        assertEquals(1, success.effects().size());
        var fx = success.effects().get(0);
        assertTrue(fx.service().isEmpty(), "ShipOrder fx should target this service");
        var ship = assertInstanceOf(ShipOrder.class, fx.command());
        assertEquals(orderId, ship.orderId());
    }

    @Test
    void cancelOrderUsesEnumSwitchOnAggregateState() {
        UUID orderId = UUID.randomUUID();
        OrderAggregate shipped = new OrderAggregate(
                orderId, 5, OrderStatus.SHIPPED, UUID.randomUUID(), UUID.randomUUID(), 1, Money.usd(1000), "TRACK-1");

        Application app = build((_, _) -> shipped, (_, _) -> {
            throw new IllegalStateException();
        });

        CommandResponse resp =
                app.dispatch(new CancelOrder(UUID.randomUUID(), orderId, "changed mind"), RequestMeta.newRequest());

        var failure = assertInstanceOf(CommandResponse.Failure.class, resp);
        assertEquals("already-shipped", failure.code());
    }

    @Test
    void aggregateApplyExhaustivelyHandlesEveryEvent() {
        OrderAggregate agg = OrderAggregate.initial(UUID.randomUUID());

        OrderAggregate afterPlaced =
                agg.applyEvent(new OrderPlaced(agg.id(), UUID.randomUUID(), UUID.randomUUID(), 2, Money.usd(2000)));
        assertEquals(OrderStatus.PLACED, afterPlaced.status());

        OrderAggregate afterPaid = afterPlaced.applyEvent(new PaymentConfirmed(agg.id(), Money.usd(2000)));
        assertEquals(OrderStatus.PAID, afterPaid.status());

        OrderAggregate afterShipped = afterPaid.applyEvent(new OrderShipped(agg.id(), "T-1"));
        assertEquals(OrderStatus.SHIPPED, afterShipped.status());
        assertEquals("T-1", afterShipped.trackingNumber());

        OrderAggregate afterCancelled = afterPlaced.applyEvent(new OrderCancelled(agg.id(), "oops"));
        assertEquals(OrderStatus.CANCELLED, afterCancelled.status());
    }

    @Test
    void queryReturnsTypedResponse() {
        UUID orderId = UUID.randomUUID();
        OrderAggregate stored = new OrderAggregate(
                orderId, 2, OrderStatus.PAID, UUID.randomUUID(), UUID.randomUUID(), 1, Money.usd(500), null);
        Application app = build((_, _) -> stored, (_, _) -> null);

        OrderAggregate result = app.query(OrderIds.GET_ORDER, new GetOrder(orderId), RequestMeta.newRequest());

        assertEquals(orderId, result.id());
        assertEquals(OrderStatus.PAID, result.status());
    }

    @Test
    void unknownLocalQueryFailsAtBuild() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> OrderModule.register(Application.builder("order-svc")).build());
        assertTrue(ex.getMessage().contains("get-order"));
    }
}
