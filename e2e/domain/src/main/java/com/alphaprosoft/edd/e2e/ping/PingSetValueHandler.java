package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

public final class PingSetValueHandler
    implements CommandHandler<PingSetValueCommand, PingAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PingAggregate> ctx, PingSetValueCommand cmd) {
    return List.of(PingValueSetEvent.builder().id(cmd.id()).value(cmd.value()).build());
  }
}
