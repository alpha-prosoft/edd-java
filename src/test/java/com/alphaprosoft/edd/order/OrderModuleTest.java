package com.alphaprosoft.edd.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.alphaprosoft.edd.Application;
import com.alphaprosoft.edd.CommandResponse;
import com.alphaprosoft.edd.QueryHandler;
import com.alphaprosoft.edd.RequestMeta;
import com.alphaprosoft.edd.order.command.CancelOrderCommand;
import com.alphaprosoft.edd.order.command.ConfirmPaymentCommand;
import com.alphaprosoft.edd.order.command.PlaceOrderCommand;
import com.alphaprosoft.edd.order.command.ShipOrderCommand;
import com.alphaprosoft.edd.order.event.OrderCancelledEvent;
import com.alphaprosoft.edd.order.event.OrderPlacedEvent;
import com.alphaprosoft.edd.order.event.OrderShippedEvent;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;
import com.alphaprosoft.edd.order.query.GetOrderQuery;

class OrderModuleTest {

    private static Application.Builder builderWith(
            QueryHandler<GetOrderQuery, OrderAggregate> getOrder,
            QueryHandler<com.alphaprosoft.edd.order.query.GetCustomerQuery, Customer> getCustomer,
            QueryHandler<com.alphaprosoft.edd.order.query.GetProductQuery, Product> getProduct) {
        return Application.builder("order-svc")
                .module(OrderAggregate.class, OrderModule::register)
                .regQuery(QueryRegistry.GET_ORDER, getOrder)
                .regQuery(QueryRegistry.GET_CUSTOMER, getCustomer)
                .regQuery(QueryRegistry.GET_PRODUCT, getProduct);
    }

    @Test
    void modulesRegistersWithoutErrors() {
        Application app =
                builderWith((_, _) -> null, (_, _) -> null, (_, _) -> null).build();
        assertEquals("order-svc", app.serviceName());
    }

    @Test
    void placeOrderHappyPath() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Customer customer = new Customer(customerId, "Alice", Customer.Tier.GOLD);
        Product product = new Product(productId, "Widget", Money.usd(1000), 50);

        Application app = builderWith((_, _) -> null, (_, _) -> customer, (_, _) -> product)
                .build();

        UUID cmdId = UUID.randomUUID();
        CommandResponse resp =
                app.dispatch(new PlaceOrderCommand(cmdId, customerId, productId, 3), RequestMeta.newRequest());

        var success = assertInstanceOf(CommandResponse.Success.class, resp);
        assertEquals(1, success.events().size());
        var placed = assertInstanceOf(OrderPlacedEvent.class, success.events().getFirst());
        assertEquals(cmdId, placed.id());
        assertEquals(3, placed.quantity());
        assertEquals(3000, placed.total().amountCents());

        assertEquals(0, success.effects().size(), "OrderPlaced has no fx in this example");
    }

    @Test
    void placeOrderRejectsLowStock() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Customer customer = new Customer(customerId, "Bob", Customer.Tier.STANDARD);
        Product product = new Product(productId, "Scarce", Money.usd(500), 1);

        Application app = builderWith((_, _) -> null, (_, _) -> customer, (_, _) -> product)
                .build();

        CommandResponse resp = app.dispatch(
                new PlaceOrderCommand(UUID.randomUUID(), customerId, productId, 5), RequestMeta.newRequest());

        var failure = assertInstanceOf(CommandResponse.Failure.class, resp);
        assertEquals("insufficient-stock", failure.code());
    }

    @Test
    void confirmPaymentUsesIdFnAndProducesShipFx() {
        UUID orderId = UUID.randomUUID();
        OrderAggregate placed = new OrderAggregate(
                orderId, 1, OrderStatus.PLACED, UUID.randomUUID(), UUID.randomUUID(), 2, Money.usd(2000), null);

        Application app =
                builderWith((_, _) -> placed, (_, _) -> null, (_, _) -> null).build();

        UUID cmdId = UUID.randomUUID();
        CommandResponse resp =
                app.dispatch(new ConfirmPaymentCommand(cmdId, orderId, Money.usd(2000)), RequestMeta.newRequest());

        var success = assertInstanceOf(CommandResponse.Success.class, resp);
        assertEquals(orderId, success.aggregateId(), "id fn should map command id to orderId");
        var confirmed =
                assertInstanceOf(PaymentConfirmedEvent.class, success.events().getFirst());
        assertEquals(orderId, confirmed.id());

        assertEquals(1, success.effects().size());
        var ship = assertInstanceOf(ShipOrderCommand.class, success.effects().getFirst());
        assertEquals(orderId, ship.orderId());
    }

    @Test
    void cancelOrderUsesEnumSwitchOnAggregateState() {
        UUID orderId = UUID.randomUUID();
        OrderAggregate shipped = new OrderAggregate(
                orderId, 5, OrderStatus.SHIPPED, UUID.randomUUID(), UUID.randomUUID(), 1, Money.usd(1000), "TRACK-1");

        Application app =
                builderWith((_, _) -> shipped, (_, _) -> null, (_, _) -> null).build();

        CommandResponse resp = app.dispatch(
                new CancelOrderCommand(UUID.randomUUID(), orderId, "changed mind"), RequestMeta.newRequest());

        var failure = assertInstanceOf(CommandResponse.Failure.class, resp);
        assertEquals("already-shipped", failure.code());
    }

    @Test
    void perEventApplyMethodsBuildAggregateState() {
        OrderAggregate agg = OrderAggregate.initial(UUID.randomUUID());

        OrderAggregate afterPlaced = OrderAggregate.placed(
                agg, new OrderPlacedEvent(agg.id(), UUID.randomUUID(), UUID.randomUUID(), 2, Money.usd(2000)));
        assertEquals(OrderStatus.PLACED, afterPlaced.status());

        OrderAggregate afterPaid =
                OrderAggregate.paid(afterPlaced, new PaymentConfirmedEvent(agg.id(), Money.usd(2000)));
        assertEquals(OrderStatus.PAID, afterPaid.status());

        OrderAggregate afterShipped = OrderAggregate.shipped(afterPaid, new OrderShippedEvent(agg.id(), "T-1"));
        assertEquals(OrderStatus.SHIPPED, afterShipped.status());
        assertEquals("T-1", afterShipped.trackingNumber());

        OrderAggregate afterCancelled =
                OrderAggregate.cancelled(afterPlaced, new OrderCancelledEvent(agg.id(), "oops"));
        assertEquals(OrderStatus.CANCELLED, afterCancelled.status());
    }

    @Test
    void queryReturnsTypedResponse() {
        UUID orderId = UUID.randomUUID();
        OrderAggregate stored = new OrderAggregate(
                orderId, 2, OrderStatus.PAID, UUID.randomUUID(), UUID.randomUUID(), 1, Money.usd(500), null);
        Application app =
                builderWith((_, _) -> stored, (_, _) -> null, (_, _) -> null).build();

        OrderAggregate result =
                app.query(QueryRegistry.GET_ORDER, new GetOrderQuery(orderId), RequestMeta.newRequest());

        assertEquals(orderId, result.id());
        assertEquals(OrderStatus.PAID, result.status());
    }

    @Test
    void unknownQueryFailsAtBuild() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Application.builder("order-svc")
                        .module(OrderAggregate.class, OrderModule::register)
                        .build());
        assertTrue(ex.getMessage().contains("get-order") || ex.getMessage().contains("get-customer"));
    }
}
