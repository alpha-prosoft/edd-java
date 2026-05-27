package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

public final class PingHandler implements CommandHandler<PingCommand, PingAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PingAggregate> ctx, PingCommand cmd) {
    return List.of(PingedEvent.builder().id(cmd.id()).hops(cmd.hops()).build());
  }
}
