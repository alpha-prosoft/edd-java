package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.CommandHandler;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.HandlerResult;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.event.OrderCancelledEvent;

public final class CancelOrderHandler implements CommandHandler<CancelOrderCommand, OrderAggregate> {

    @Override
    public HandlerResult<OrderAggregate> handle(Context ctx, CancelOrderCommand cmd) {
        OrderAggregate order = ctx.getDeps(OrderDeps.CURRENT_ORDER);
        return switch (order.status()) {
            case PLACED, PAID ->
                HandlerResult.of(OrderCancelledEvent.builder()
                        .id(cmd.orderId())
                        .reason(cmd.reason())
                        .build());
            case SHIPPED -> HandlerResult.error("already-shipped");
            case CANCELLED -> HandlerResult.error("already-cancelled");
            case NEW -> HandlerResult.error("not-placed");
        };
    }
}
