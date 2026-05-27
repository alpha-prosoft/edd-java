package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

public final class PongHandler implements CommandHandler<PongCommand, PongAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PongAggregate> ctx, PongCommand cmd) {
    return List.of(PongedEvent.builder().id(cmd.id()).hops(cmd.hops()).build());
  }
}
