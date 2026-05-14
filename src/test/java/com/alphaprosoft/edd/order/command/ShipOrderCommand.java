package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;

public record ShipOrderCommand(UUID id, UUID orderId, String trackingNumber) implements Command {}
