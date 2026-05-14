package com.alphaprosoft.edd.order.event;

import java.util.UUID;

import com.alphaprosoft.edd.order.Money;

public record PaymentConfirmed(UUID id, Money amount) implements OrderEvent {}
