package com.alphaprosoft.edd.order.event;

import java.util.UUID;

public record OrderCancelled(UUID id, String reason) implements OrderEvent {}
