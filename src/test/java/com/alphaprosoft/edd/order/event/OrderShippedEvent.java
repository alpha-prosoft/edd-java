package com.alphaprosoft.edd.order.event;

import java.util.UUID;

public record OrderShippedEvent(UUID id, String trackingNumber) implements OrderEvent {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private String trackingNumber;

        private Builder() {}

        public Builder from(OrderShippedEvent e) {
            this.id = e.id;
            this.trackingNumber = e.trackingNumber;
            return this;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public OrderShippedEvent build() {
            return new OrderShippedEvent(id, trackingNumber);
        }
    }
}
