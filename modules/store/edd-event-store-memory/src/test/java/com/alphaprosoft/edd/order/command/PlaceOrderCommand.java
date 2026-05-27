package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

public record PlaceOrderCommand(UUID id, UUID customerId, UUID productId, int quantity)
    implements Command {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(PlaceOrderCommand existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;
    private UUID customerId;
    private UUID productId;
    private int quantity;

    private Builder() {}

    private Builder(PlaceOrderCommand c) {
      this.id = c.id;
      this.customerId = c.customerId;
      this.productId = c.productId;
      this.quantity = c.quantity;
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

    public PlaceOrderCommand build() {
      return new PlaceOrderCommand(id, customerId, productId, quantity);
    }
  }
}
