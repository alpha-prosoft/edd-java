package com.alphaprosoft.edd.order;

public record Money(long amountCents, String currency) {

    public static Money usd(long cents) {
        return new Money(cents, "USD");
    }

    public Money times(int n) {
        return new Money(amountCents * n, currency);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Money existing) {
        return new Builder(existing);
    }

    public static final class Builder {

        private long amountCents;
        private String currency;

        private Builder() {}

        private Builder(Money m) {
            this.amountCents = m.amountCents;
            this.currency = m.currency;
        }

        public Builder amountCents(long amountCents) {
            this.amountCents = amountCents;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Money build() {
            return new Money(amountCents, currency);
        }
    }
}
