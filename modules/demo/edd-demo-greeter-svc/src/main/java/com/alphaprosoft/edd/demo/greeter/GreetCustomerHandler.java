package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.demo.customer.Customer;
import java.util.List;

public final class GreetCustomerHandler
    implements CommandHandler<GreetCustomerCommand, GreeterAggregate> {

  @Override
  public List<CommandEmission> handle(
      CommandContext<GreeterAggregate> ctx, GreetCustomerCommand cmd) {
    Customer customer = ctx.getDeps(GreeterIds.CUSTOMER); // resolved remotely from customer-svc
    return List.of(new CustomerGreetedEvent(cmd.id(), "Hello, " + customer.name() + "!"));
  }
}
