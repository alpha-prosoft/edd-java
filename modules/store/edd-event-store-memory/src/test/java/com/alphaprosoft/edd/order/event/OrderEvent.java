package com.alphaprosoft.edd.order.event;

import com.alphaprosoft.edd.command.Event;

public sealed interface OrderEvent extends Event
    permits OrderPlacedEvent, PaymentConfirmedEvent, OrderCancelledEvent, OrderShippedEvent {}
