package com.alphaprosoft.edd.order;

import java.util.UUID;

public record Customer(UUID id, String name, Tier tier) {
    public enum Tier {
        STANDARD,
        GOLD,
        PLATINUM
    }
}
