package com.alphaprosoft.edd.order;

import java.util.UUID;

public record Product(UUID id, String name, Money price, int stock) {}
