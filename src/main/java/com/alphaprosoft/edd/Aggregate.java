package com.alphaprosoft.edd;

import java.util.UUID;

public interface Aggregate {
    UUID id();

    long version();
}
