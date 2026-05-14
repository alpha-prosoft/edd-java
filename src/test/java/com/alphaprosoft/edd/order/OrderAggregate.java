package com.alphaprosoft.edd.order;

import java.util.UUID;

import com.alphaprosoft.edd.Aggregate;
import com.alphaprosoft.edd.order.event.OrderCancelled;
import com.alphaprosoft.edd.order.event.OrderEvent;
import com.alphaprosoft.edd.order.event.OrderPlaced;
import com.alphaprosoft.edd.order.event.OrderShipped;
import com.alphaprosoft.edd.order.event.PaymentConfirmed;

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

    public OrderAggregate applyEvent(OrderEvent event) {
        return switch (event) {
            case OrderPlaced e ->
                new OrderAggregate(
                        e.id(),
                        version + 1,
                        OrderStatus.PLACED,
                        e.customerId(),
                        e.productId(),
                        e.quantity(),
                        e.total(),
                        null);
            case PaymentConfirmed _ ->
                new OrderAggregate(
                        id, version + 1, OrderStatus.PAID, customerId, productId, quantity, total, trackingNumber);
            case OrderCancelled _ ->
                new OrderAggregate(
                        id, version + 1, OrderStatus.CANCELLED, customerId, productId, quantity, total, trackingNumber);
            case OrderShipped e ->
                new OrderAggregate(
                        id,
                        version + 1,
                        OrderStatus.SHIPPED,
                        customerId,
                        productId,
                        quantity,
                        total,
                        e.trackingNumber());
        };
    }
}
