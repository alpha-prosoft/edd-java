package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;

public record PlaceOrderCommand(UUID id, UUID customerId, UUID productId, int quantity) implements Command {}
