package com.alphaprosoft.edd.order.event;

import java.util.UUID;

public record OrderCancelledEvent(UUID id, String reason) implements OrderEvent {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private String reason;

        private Builder() {}

        public Builder from(OrderCancelledEvent e) {
            this.id = e.id;
            this.reason = e.reason;
            return this;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public OrderCancelledEvent build() {
            return new OrderCancelledEvent(id, reason);
        }
    }
}
