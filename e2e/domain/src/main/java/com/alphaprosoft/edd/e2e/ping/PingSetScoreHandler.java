package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

public final class PingSetScoreHandler
    implements CommandHandler<PingSetScoreCommand, PingAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PingAggregate> ctx, PingSetScoreCommand cmd) {
    return List.of(ScoreSetEvent.builder().id(cmd.id()).score(cmd.score()).build());
  }
}
