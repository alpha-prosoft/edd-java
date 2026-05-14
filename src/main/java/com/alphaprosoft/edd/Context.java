package com.alphaprosoft.edd;

import java.util.UUID;

public interface Context {

    <T> T get(Dep<?, T> key);

    boolean has(Dep<?, ?> key);

    UUID requestId();

    UUID interactionId();
}
