package com.alphaprosoft.edd;

import java.util.Map;
import java.util.UUID;

record ContextImpl(Map<String, Object> deps, UUID requestId, UUID interactionId) implements Context {

    @Override
    public <T> T get(Dep<?, T> key) {
        if (!deps.containsKey(key.name())) {
            throw new IllegalStateException("Dep not resolved: " + key.name());
        }
        @SuppressWarnings("unchecked")
        T value = (T) deps.get(key.name());
        return value;
    }

    @Override
    public boolean has(Dep<?, ?> key) {
        return deps.containsKey(key.name());
    }
}
