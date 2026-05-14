package com.alphaprosoft.edd;

import java.util.UUID;

public record RequestMeta(UUID requestId, UUID interactionId) {
    public static RequestMeta newRequest() {
        return new RequestMeta(UUID.randomUUID(), UUID.randomUUID());
    }
}
