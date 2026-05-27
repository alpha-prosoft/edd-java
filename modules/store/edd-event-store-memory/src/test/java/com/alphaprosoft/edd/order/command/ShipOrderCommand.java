package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

public record ShipOrderCommand(UUID id, UUID orderId, String trackingNumber) implements Command {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(ShipOrderCommand existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;
    private UUID orderId;
    private String trackingNumber;

    private Builder() {}

    private Builder(ShipOrderCommand c) {
      this.id = c.id;
      this.orderId = c.orderId;
      this.trackingNumber = c.trackingNumber;
    }

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder orderId(UUID orderId) {
      this.orderId = orderId;
      return this;
    }

    public Builder trackingNumber(String trackingNumber) {
      this.trackingNumber = trackingNumber;
      return this;
    }

    public ShipOrderCommand build() {
      return new ShipOrderCommand(id, orderId, trackingNumber);
    }
  }
}
