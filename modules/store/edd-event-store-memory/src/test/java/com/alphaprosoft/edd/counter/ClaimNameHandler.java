package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.Identity;
import java.util.List;

public final class ClaimNameHandler implements CommandHandler<ClaimNameCommand, CounterAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<CounterAggregate> ctx, ClaimNameCommand cmd) {
    return List.of(
        new NameClaimedEvent(cmd.id(), cmd.name()), Identity.builder().name(cmd.name()).build());
  }
}
