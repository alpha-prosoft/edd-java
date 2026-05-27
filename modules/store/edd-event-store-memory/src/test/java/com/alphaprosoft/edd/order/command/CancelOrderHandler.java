package com.alphaprosoft.edd.order.command;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.Rejection;
import com.alphaprosoft.edd.order.OrderAggregate;
import com.alphaprosoft.edd.order.OrderDeps;
import com.alphaprosoft.edd.order.event.OrderCancelledEvent;
import java.util.List;

public final class CancelOrderHandler
    implements CommandHandler<CancelOrderCommand, OrderAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<OrderAggregate> ctx, CancelOrderCommand cmd) {
    OrderAggregate order = ctx.getDeps(OrderDeps.CURRENT_ORDER);
    return switch (order.status()) {
      case PLACED, PAID ->
          List.of(OrderCancelledEvent.builder().id(cmd.orderId()).reason(cmd.reason()).build());
      case SHIPPED -> List.of(Rejection.of("already-shipped"));
      case CANCELLED -> List.of(Rejection.of("already-cancelled"));
      case NEW -> List.of(Rejection.of("not-placed"));
    };
  }
}
