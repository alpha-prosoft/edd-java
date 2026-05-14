package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.CommandHandler;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.HandlerResult;
import com.alphaprosoft.edd.order.Customer;
import com.alphaprosoft.edd.order.Money;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.Product;
import com.alphaprosoft.edd.order.event.OrderPlacedEvent;

public final class PlaceOrderHandler implements CommandHandler<PlaceOrderCommand, OrderAggregate> {

    @Override
    public HandlerResult<OrderAggregate> handle(Context ctx, PlaceOrderCommand cmd) {
        Customer customer = ctx.getDeps(OrderDeps.CUSTOMER);
        Product product = ctx.getDeps(OrderDeps.PRODUCT);

        if (product.stock() < cmd.quantity()) {
            return HandlerResult.error("insufficient-stock");
        }

        Money total = product.price().times(cmd.quantity());
        return HandlerResult.of(new OrderPlacedEvent(cmd.id(), customer.id(), product.id(), cmd.quantity(), total));
    }
}
