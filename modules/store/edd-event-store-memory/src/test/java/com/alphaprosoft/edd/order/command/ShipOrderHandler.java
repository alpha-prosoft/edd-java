package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.Rejection;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.OrderStatus;
import com.alphaprosoft.edd.order.event.OrderShippedEvent;
import java.util.List;

public final class ShipOrderHandler implements CommandHandler<ShipOrderCommand, OrderAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<OrderAggregate> ctx, ShipOrderCommand cmd) {
    OrderAggregate order = ctx.getDeps(OrderDeps.CURRENT_ORDER);
    if (order.status() != OrderStatus.PAID) {
      return List.of(Rejection.of("not-paid"));
    }
    return List.of(
        OrderShippedEvent.builder().id(cmd.orderId()).trackingNumber(cmd.trackingNumber()).build());
  }
}
