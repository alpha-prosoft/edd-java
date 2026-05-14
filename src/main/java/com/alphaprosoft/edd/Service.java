package com.alphaprosoft.edd;

public record Service(String name) {
    public static Service of(String name) {
        return new Service(name);
    }
}
