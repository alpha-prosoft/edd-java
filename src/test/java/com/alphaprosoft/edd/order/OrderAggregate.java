package com.alphaprosoft.edd.order;

import java.util.UUID;

import com.alphaprosoft.edd.Aggregate;
import com.alphaprosoft.edd.order.event.OrderCancelledEvent;
import com.alphaprosoft.edd.order.event.OrderPlacedEvent;
import com.alphaprosoft.edd.order.event.OrderShippedEvent;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;

public record OrderAggregate(
        UUID id,
        long version,
        OrderStatus status,
        UUID customerId,
        UUID productId,
        int quantity,
        Money total,
        String trackingNumber)
        implements Aggregate {

    public static OrderAggregate initial(UUID id) {
        return new OrderAggregate(id, 0, OrderStatus.NEW, null, null, 0, null, null);
    }

    public static OrderAggregate placed(OrderAggregate agg, OrderPlacedEvent e) {
        return new OrderAggregate(
                e.id(), 1, OrderStatus.PLACED, e.customerId(), e.productId(), e.quantity(), e.total(), null);
    }

    public static OrderAggregate paid(OrderAggregate agg, PaymentConfirmedEvent event) {
        return new OrderAggregate(
                agg.id(),
                agg.version() + 1,
                OrderStatus.PAID,
                agg.customerId(),
                agg.productId(),
                agg.quantity(),
                agg.total(),
                agg.trackingNumber());
    }

    public static OrderAggregate cancelled(OrderAggregate agg, OrderCancelledEvent event) {
        return new OrderAggregate(
                agg.id(),
                agg.version() + 1,
                OrderStatus.CANCELLED,
                agg.customerId(),
                agg.productId(),
                agg.quantity(),
                agg.total(),
                agg.trackingNumber());
    }

    public static OrderAggregate shipped(OrderAggregate agg, OrderShippedEvent e) {
        return new OrderAggregate(
                agg.id(),
                agg.version() + 1,
                OrderStatus.SHIPPED,
                agg.customerId(),
                agg.productId(),
                agg.quantity(),
                agg.total(),
                e.trackingNumber());
    }
}
