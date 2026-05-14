package com.alphaprosoft.edd.order;

import java.util.UUID;

public record Customer(UUID id, String name, Tier tier) {

    public enum Tier {
        STANDARD,
        GOLD,
        PLATINUM
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private String name;
        private Tier tier;

        private Builder() {}

        public Builder from(Customer c) {
            this.id = c.id;
            this.name = c.name;
            this.tier = c.tier;
            return this;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder tier(Tier tier) {
            this.tier = tier;
            return this;
        }

        public Customer build() {
            return new Customer(id, name, tier);
        }
    }
}
