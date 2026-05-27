package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

public final class PingBroadcastHandler
    implements CommandHandler<PingBroadcastCommand, PingAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PingAggregate> ctx, PingBroadcastCommand cmd) {
    return List.of(
        BroadcastedEvent.builder()
            .id(cmd.id())
            .pingTarget(cmd.pingTarget())
            .pongTarget(cmd.pongTarget())
            .value(cmd.value())
            .build());
  }
}
