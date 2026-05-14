package com.alphaprosoft.edd.order.event;

import java.util.UUID;

public record OrderShippedEvent(UUID id, String trackingNumber) implements OrderEvent {}
