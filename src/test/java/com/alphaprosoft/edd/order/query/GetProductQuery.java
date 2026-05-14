package com.alphaprosoft.edd.order.query;

import java.util.UUID;

import com.alphaprosoft.edd.Query;

public record GetProductQuery(UUID id) implements Query {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;

        private Builder() {}

        public Builder from(GetProductQuery q) {
            this.id = q.id;
            return this;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public GetProductQuery build() {
            return new GetProductQuery(id);
        }
    }
}
