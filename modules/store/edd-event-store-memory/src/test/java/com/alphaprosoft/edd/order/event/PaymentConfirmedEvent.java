package com.alphaprosoft.edd.order.event;

import com.alphaprosoft.edd.order.Money;
import java.util.UUID;

public record PaymentConfirmedEvent(UUID id, Money amount) implements OrderEvent {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(PaymentConfirmedEvent existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;
    private Money amount;

    private Builder() {}

    private Builder(PaymentConfirmedEvent e) {
      this.id = e.id;
      this.amount = e.amount;
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
