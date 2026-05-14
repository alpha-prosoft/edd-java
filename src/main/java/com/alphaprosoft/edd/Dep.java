package com.alphaprosoft.edd;

import java.util.Objects;

public record Dep<Q extends Query, T>(String name, QueryId<Q, T> queryId) {

    public Dep {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(queryId, "queryId");
    }

    public static <Q extends Query, T> Dep<Q, T> of(String name, QueryId<Q, T> queryId) {
        return new Dep<>(name, queryId);
    }

    @Override
    public String toString() {
        return "Dep[" + name + " -> " + queryId + "]";
    }
}
