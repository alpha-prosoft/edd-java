package com.alphaprosoft.edd.order.event;

import java.util.UUID;

public record OrderShippedEvent(UUID id, String trackingNumber) implements OrderEvent {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(OrderShippedEvent existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;
    private String trackingNumber;

    private Builder() {}

    private Builder(OrderShippedEvent e) {
      this.id = e.id;
      this.trackingNumber = e.trackingNumber;
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
