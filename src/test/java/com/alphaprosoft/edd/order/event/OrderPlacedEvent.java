package com.alphaprosoft.edd.order.event;

import java.util.UUID;

import com.alphaprosoft.edd.order.Money;

public record OrderPlacedEvent(UUID id, UUID customerId, UUID productId, int quantity, Money total)
        implements OrderEvent {}
