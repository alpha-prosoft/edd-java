package com.alphaprosoft.edd.demo.customer;

import java.util.UUID;

/** Query response — the contract other services consume. */
public record Customer(UUID id, String name) {}
