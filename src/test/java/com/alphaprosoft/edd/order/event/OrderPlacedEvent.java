package com.alphaprosoft.edd.order.event;

import java.util.UUID;

import com.alphaprosoft.edd.order.Money;

public record OrderPlacedEvent(UUID id, UUID customerId, UUID productId, int quantity, Money total)
        implements OrderEvent {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(OrderPlacedEvent existing) {
        return new Builder(existing);
    }

    public static final class Builder {

        private UUID id;
        private UUID customerId;
        private UUID productId;
        private int quantity;
        private Money total;

        private Builder() {}

        private Builder(OrderPlacedEvent e) {
            this.id = e.id;
            this.customerId = e.customerId;
            this.productId = e.productId;
            this.quantity = e.quantity;
            this.total = e.total;
        }

        public Builder id(UUID id) {
            this.id = id;
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

        public OrderPlacedEvent build() {
            return new OrderPlacedEvent(id, customerId, productId, quantity, total);
        }
    }
}
