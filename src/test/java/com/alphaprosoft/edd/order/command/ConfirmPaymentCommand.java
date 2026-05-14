package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;
import com.alphaprosoft.edd.order.Money;

public record ConfirmPaymentCommand(UUID id, UUID orderId, Money amount) implements Command {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private UUID orderId;
        private Money amount;

        private Builder() {}

        public Builder from(ConfirmPaymentCommand c) {
            this.id = c.id;
            this.orderId = c.orderId;
            this.amount = c.amount;
            return this;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public ConfirmPaymentCommand build() {
            return new ConfirmPaymentCommand(id, orderId, amount);
        }
    }
}
