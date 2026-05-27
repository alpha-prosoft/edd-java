package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.Identity;
import com.alphaprosoft.edd.command.Rejection;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.OrderStatus;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;
import java.util.List;

public final class ConfirmPaymentHandler
    implements CommandHandler<ConfirmPaymentCommand, OrderAggregate> {

  @Override
  public List<CommandEmission> handle(
      CommandContext<OrderAggregate> ctx, ConfirmPaymentCommand cmd) {
    OrderAggregate order = ctx.getDeps(OrderDeps.CURRENT_ORDER);
    if (order.status() != OrderStatus.PLACED) {
      return List.of(Rejection.of("invalid-status"));
    }
    if (cmd.amount().amountCents() != order.total().amountCents()) {
      return List.of(Rejection.of("amount-mismatch"));
    }
    // Emit an event and reserve a uniqueness key in one response (edd-core's create-1 pattern):
    // the payment reference must be unique across the service.
    return List.of(
        PaymentConfirmedEvent.builder().id(cmd.orderId()).amount(cmd.amount()).build(),
        Identity.builder().name("payment-" + cmd.orderId()).build());
  }
}
