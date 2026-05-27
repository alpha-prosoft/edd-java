package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

public final class PingObjectUploadedHandler
    implements CommandHandler<PingObjectUploadedCommand, PingAggregate> {

  @Override
  public List<CommandEmission> handle(
      CommandContext<PingAggregate> ctx, PingObjectUploadedCommand cmd) {
    return List.of(
        ObjectRecordedEvent.builder().id(cmd.id()).bucket(cmd.bucket()).key(cmd.key()).build());
  }
}
