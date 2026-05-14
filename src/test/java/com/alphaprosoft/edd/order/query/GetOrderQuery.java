package com.alphaprosoft.edd.order.query;

import java.util.UUID;

import com.alphaprosoft.edd.Query;

public record GetOrderQuery(UUID id) implements Query {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(GetOrderQuery existing) {
        return new Builder(existing);
    }

    public static final class Builder {

        private UUID id;

        private Builder() {}

        private Builder(GetOrderQuery q) {
            this.id = q.id;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public GetOrderQuery build() {
            return new GetOrderQuery(id);
        }
    }
}
