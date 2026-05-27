package com.alphaprosoft.edd.crossservice;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.order.Customer;
import java.util.List;

public final class GreetHandler implements CommandHandler<GreetCommand, GreeterAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<GreeterAggregate> ctx, GreetCommand cmd) {
    Customer customer = ctx.getDeps(GreeterRegistry.CUSTOMER); // resolved from customer-svc
    return List.of(new GreetedEvent(cmd.id(), "Hello " + customer.name()));
  }
}
