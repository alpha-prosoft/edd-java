package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.CommandHandler;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.HandlerResult;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.OrderStatus;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;

public final class ConfirmPaymentHandler implements CommandHandler<ConfirmPaymentCommand, OrderAggregate> {

    @Override
    public HandlerResult<OrderAggregate> handle(Context ctx, ConfirmPaymentCommand cmd) {
        OrderAggregate order = ctx.getDeps(OrderDeps.CURRENT_ORDER);
        if (order.status() != OrderStatus.PLACED) {
            return HandlerResult.error("invalid-status");
        }
        if (cmd.amount().amountCents() != order.total().amountCents()) {
            return HandlerResult.error("amount-mismatch");
        }
        return HandlerResult.of(new PaymentConfirmedEvent(cmd.orderId(), cmd.amount()));
    }
}
