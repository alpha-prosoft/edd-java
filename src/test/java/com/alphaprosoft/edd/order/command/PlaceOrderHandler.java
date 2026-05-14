package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.CommandHandler;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.HandlerResult;
import com.alphaprosoft.edd.order.Customer;
import com.alphaprosoft.edd.order.Money;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.Product;
import com.alphaprosoft.edd.order.event.OrderPlaced;

public final class PlaceOrderHandler implements CommandHandler<PlaceOrder, OrderAggregate> {

    @Override
    public HandlerResult<OrderAggregate> handle(Context ctx, PlaceOrder cmd) {
        Customer customer = ctx.get(OrderDeps.CUSTOMER);
        Product product = ctx.get(OrderDeps.PRODUCT);

        if (product.stock() < cmd.quantity()) {
            return HandlerResult.error("insufficient-stock");
        }

        Money total = product.price().times(cmd.quantity());
        return HandlerResult.of(new OrderPlaced(cmd.id(), customer.id(), product.id(), cmd.quantity(), total));
    }
}
