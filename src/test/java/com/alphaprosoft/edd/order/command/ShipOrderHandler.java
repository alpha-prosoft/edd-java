package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.CommandHandler;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.HandlerResult;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.OrderStatus;
import com.alphaprosoft.edd.order.event.OrderShipped;

public final class ShipOrderHandler implements CommandHandler<ShipOrder, OrderAggregate> {

    @Override
    public HandlerResult<OrderAggregate> handle(Context ctx, ShipOrder cmd) {
        OrderAggregate order = ctx.get(OrderDeps.CURRENT_ORDER);
        if (order.status() != OrderStatus.PAID) {
            return HandlerResult.error("not-paid");
        }
        return HandlerResult.of(new OrderShipped(cmd.orderId(), cmd.trackingNumber()));
    }
}
