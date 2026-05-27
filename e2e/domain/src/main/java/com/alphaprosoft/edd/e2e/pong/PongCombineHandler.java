package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.e2e.ping.PingAggregate;
import java.util.List;

/**
 * Reads ping's aggregate (a remote dep resolved from ping-svc) and folds its value+version into a
 * pong event.
 */
public final class PongCombineHandler implements CommandHandler<PongCombineCommand, PongAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PongAggregate> ctx, PongCombineCommand cmd) {
    PingAggregate ping = ctx.getDeps(PongDeps.PING);
    return List.of(
        CombinedEvent.builder()
            .id(cmd.id())
            .pingValue(ping == null ? null : ping.value())
            .pingVersion(ping == null ? 0 : ping.version())
            .pongValue(cmd.value())
            .build());
  }
}
