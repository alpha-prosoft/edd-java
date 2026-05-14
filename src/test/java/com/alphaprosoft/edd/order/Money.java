package com.alphaprosoft.edd.order;

public record Money(long amountCents, String currency) {
    public static Money usd(long cents) {
        return new Money(cents, "USD");
    }

    public Money times(int n) {
        return new Money(amountCents * n, currency);
    }
}
