package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.Rejection;
import com.alphaprosoft.edd.order.Customer;
import com.alphaprosoft.edd.order.Money;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.Product;
import com.alphaprosoft.edd.order.event.OrderPlacedEvent;
import java.util.List;

public final class PlaceOrderHandler implements CommandHandler<PlaceOrderCommand, OrderAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<OrderAggregate> ctx, PlaceOrderCommand cmd) {
    Customer customer = ctx.getDeps(OrderDeps.CUSTOMER);
    Product product = ctx.getDeps(OrderDeps.PRODUCT);

    if (product.stock() < cmd.quantity()) {
      return List.of(Rejection.of("insufficient-stock"));
    }

    Money total = product.price().times(cmd.quantity());
    return List.of(
        OrderPlacedEvent.builder()
            .id(cmd.id())
            .customerId(customer.id())
            .productId(product.id())
            .quantity(cmd.quantity())
            .total(total)
            .build());
  }
}
