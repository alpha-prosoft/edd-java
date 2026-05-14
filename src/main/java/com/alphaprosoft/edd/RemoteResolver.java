package com.alphaprosoft.edd;

@FunctionalInterface
public interface RemoteResolver {
    Object resolve(Service service, Query query);
}
