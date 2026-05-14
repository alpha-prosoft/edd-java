package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;

public record CancelOrderCommand(UUID id, UUID orderId, String reason) implements Command {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(CancelOrderCommand existing) {
        return new Builder(existing);
    }

    public static final class Builder {

        private UUID id;
        private UUID orderId;
        private String reason;

        private Builder() {}

        private Builder(CancelOrderCommand c) {
            this.id = c.id;
            this.orderId = c.orderId;
            this.reason = c.reason;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public CancelOrderCommand build() {
            return new CancelOrderCommand(id, orderId, reason);
        }
    }
}
