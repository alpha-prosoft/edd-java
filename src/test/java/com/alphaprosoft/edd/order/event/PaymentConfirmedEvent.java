package com.alphaprosoft.edd.order.event;

import java.util.UUID;

import com.alphaprosoft.edd.order.Money;

public record PaymentConfirmedEvent(UUID id, Money amount) implements OrderEvent {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private Money amount;

        private Builder() {}

        public Builder from(PaymentConfirmedEvent e) {
            this.id = e.id;
            this.amount = e.amount;
            return this;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public PaymentConfirmedEvent build() {
            return new PaymentConfirmedEvent(id, amount);
        }
    }
}
