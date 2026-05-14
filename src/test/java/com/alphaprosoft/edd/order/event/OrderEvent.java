package com.alphaprosoft.edd.order.event;

import com.alphaprosoft.edd.Event;

public sealed interface OrderEvent extends Event permits OrderPlaced, PaymentConfirmed, OrderCancelled, OrderShipped {}
