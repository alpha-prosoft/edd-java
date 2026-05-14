package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;

public record CancelOrder(UUID id, UUID orderId, String reason) implements Command {}
