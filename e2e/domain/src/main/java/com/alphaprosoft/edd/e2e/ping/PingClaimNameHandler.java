package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.Identity;
import java.util.List;

/**
 * Emits the event AND reserves a uniqueness identity in one response (the aggregate id is stamped
 * by the dispatcher).
 */
public final class PingClaimNameHandler
    implements CommandHandler<PingClaimNameCommand, PingAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<PingAggregate> ctx, PingClaimNameCommand cmd) {
    return List.of(
        NameClaimedEvent.builder().id(cmd.id()).name(cmd.name()).build(),
        Identity.builder().name("name/" + cmd.name()).build());
  }
}
