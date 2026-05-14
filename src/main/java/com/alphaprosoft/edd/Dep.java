package com.alphaprosoft.edd;

import java.util.Optional;

public final class Dep<Q extends Query, T> {

    private final String name;
    private final QueryId<Q, T> queryId;
    private final Service service;

    private Dep(String name, QueryId<Q, T> queryId, Service service) {
        this.name = name;
        this.queryId = queryId;
        this.service = service;
    }

    public static <Q extends Query, T> Dep<Q, T> local(String name, QueryId<Q, T> queryId) {
        return new Dep<>(name, queryId, null);
    }

    public static <Q extends Query, T> Dep<Q, T> remote(String name, Service service, QueryId<Q, T> queryId) {
        return new Dep<>(name, queryId, service);
    }

    public String name() {
        return name;
    }

    public QueryId<Q, T> queryId() {
        return queryId;
    }

    public Optional<Service> service() {
        return Optional.ofNullable(service);
    }

    @Override
    public String toString() {
        return "Dep[" + name + " -> " + queryId + (service != null ? " @ " + service : "") + "]";
    }
}
