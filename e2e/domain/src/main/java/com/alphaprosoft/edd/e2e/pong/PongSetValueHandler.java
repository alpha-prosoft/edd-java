package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

public final class PongSetValueHandler
    implements CommandHandler<PongSetValueCommand, PongAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PongAggregate> ctx, PongSetValueCommand cmd) {
    return List.of(PongValueSetEvent.builder().id(cmd.id()).value(cmd.value()).build());
  }
}
