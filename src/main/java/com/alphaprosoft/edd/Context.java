package com.alphaprosoft.edd;

import java.util.UUID;

public interface Context {

    <T> T getDeps(Dep<?, T> key);

    UUID requestId();

    UUID interactionId();
}
