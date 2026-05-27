package com.alphaprosoft.edd.order;

import java.util.UUID;

public record Product(UUID id, String name, Money price, int stock) {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(Product existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;
    private String name;
    private Money price;
    private int stock;

    private Builder() {}

    private Builder(Product p) {
      this.id = p.id;
      this.name = p.name;
      this.price = p.price;
      this.stock = p.stock;
    }

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder price(Money price) {
      this.price = price;
      return this;
    }

    public Builder stock(int stock) {
      this.stock = stock;
      return this;
    }

    public Product build() {
      return new Product(id, name, price, stock);
    }
  }
}
