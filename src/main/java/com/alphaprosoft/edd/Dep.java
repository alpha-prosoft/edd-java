package com.alphaprosoft.edd;

import java.util.Objects;

public record Dep<Q extends Query, T>(String name, QueryId<Q, T> queryId, Service service) {

    public Dep {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(queryId, "queryId");
    }

    public static <Q extends Query, T> Dep<Q, T> local(String name, QueryId<Q, T> queryId) {
        return new Dep<>(name, queryId, null);
    }

    public static <Q extends Query, T> Dep<Q, T> remote(String name, Service service, QueryId<Q, T> queryId) {
        return new Dep<>(name, queryId, Objects.requireNonNull(service, "service"));
    }

    public boolean isRemote() {
        return service != null;
    }

    @Override
    public String toString() {
        return "Dep[" + name + " -> " + queryId + (service != null ? " @ " + service : "") + "]";
    }
}
