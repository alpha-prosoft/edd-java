package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;
import com.alphaprosoft.edd.order.Money;

public record ConfirmPaymentCommand(UUID id, UUID orderId, Money amount) implements Command {}
