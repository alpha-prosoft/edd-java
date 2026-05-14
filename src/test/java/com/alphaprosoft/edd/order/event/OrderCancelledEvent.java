package com.alphaprosoft.edd.order.event;

import java.util.UUID;

public record OrderCancelledEvent(UUID id, String reason) implements OrderEvent {}
