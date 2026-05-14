package com.alphaprosoft.edd.order.event;

import java.util.UUID;

public record OrderShipped(UUID id, String trackingNumber) implements OrderEvent {}
