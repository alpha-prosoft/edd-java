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
        return OrderAggregate.builder()
                .from(agg)
                .id(e.id())
                .version(agg.version() + 1)
                .status(OrderStatus.PLACED)
                .customerId(e.customerId())
                .productId(e.productId())
                .quantity(e.quantity())
                .total(e.total())
                .build();
    }

    public static OrderAggregate paid(OrderAggregate agg, PaymentConfirmedEvent event) {
        return OrderAggregate.builder()
                .from(agg)
                .version(agg.version() + 1)
                .status(OrderStatus.PAID)
                .build();
    }

    public static OrderAggregate cancelled(OrderAggregate agg, OrderCancelledEvent event) {
        return OrderAggregate.builder()
                .from(agg)
                .version(agg.version() + 1)
                .status(OrderStatus.CANCELLED)
                .build();
    }

    public static OrderAggregate shipped(OrderAggregate agg, OrderShippedEvent e) {
        return OrderAggregate.builder()
                .from(agg)
                .version(agg.version() + 1)
                .status(OrderStatus.SHIPPED)
                .trackingNumber(e.trackingNumber())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private long version;
        private OrderStatus status;
        private UUID customerId;
        private UUID productId;
        private int quantity;
        private Money total;
        private String trackingNumber;

        private Builder() {}

        public Builder from(OrderAggregate a) {
            this.id = a.id;
            this.version = a.version;
            this.status = a.status;
            this.customerId = a.customerId;
            this.productId = a.productId;
            this.quantity = a.quantity;
            this.total = a.total;
            this.trackingNumber = a.trackingNumber;
            return this;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }

        public Builder customerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder productId(UUID productId) {
            this.productId = productId;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder total(Money total) {
            this.total = total;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public OrderAggregate build() {
            return new OrderAggregate(id, version, status, customerId, productId, quantity, total, trackingNumber);
        }
    }
}
