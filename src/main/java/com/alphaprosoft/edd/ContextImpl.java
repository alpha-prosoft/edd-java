package com.alphaprosoft.edd;

import java.util.Map;
import java.util.UUID;

final class ContextImpl implements Context {

    private final Map<String, Object> deps;
    private final UUID requestId;
    private final UUID interactionId;

    ContextImpl(Map<String, Object> deps, UUID requestId, UUID interactionId) {
        this.deps = deps;
        this.requestId = requestId;
        this.interactionId = interactionId;
    }

    @Override
    public <T> T getDeps(Dep<?, T> key) {
        if (!deps.containsKey(key.name())) {
            throw new IllegalStateException("Dep not resolved: " + key.name());
        }
        @SuppressWarnings("unchecked")
        T value = (T) deps.get(key.name());
        return value;
    }

    @Override
    public UUID requestId() {
        return requestId;
    }

    @Override
    public UUID interactionId() {
        return interactionId;
    }
}
