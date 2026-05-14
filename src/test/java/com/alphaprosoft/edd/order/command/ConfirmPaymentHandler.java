package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.CommandHandler;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.HandlerResult;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.OrderStatus;
import com.alphaprosoft.edd.order.event.PaymentConfirmed;

public final class ConfirmPaymentHandler implements CommandHandler<ConfirmPayment, OrderAggregate> {

    @Override
    public HandlerResult<OrderAggregate> handle(Context ctx, ConfirmPayment cmd) {
        OrderAggregate order = ctx.get(OrderDeps.CURRENT_ORDER);
        if (order.status() != OrderStatus.PLACED) {
            return HandlerResult.error("invalid-status");
        }
        if (cmd.amount().amountCents() != order.total().amountCents()) {
            return HandlerResult.error("amount-mismatch");
        }
        return HandlerResult.of(new PaymentConfirmed(cmd.orderId(), cmd.amount()));
    }
}
