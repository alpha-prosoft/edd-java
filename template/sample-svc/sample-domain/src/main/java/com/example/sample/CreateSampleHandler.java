package com.example.sample;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import java.util.List;

/** Pure: validate, decide, emit. The framework creates a fresh instance per dispatch. */
public final class CreateSampleHandler
    implements CommandHandler<CreateSampleCommand, SampleAggregate> {

  @Override
  public List<CommandEmission> handle(
      CommandContext<SampleAggregate> ctx, CreateSampleCommand cmd) {
    return List.of(new SampleCreatedEvent(cmd.id(), cmd.name()));
  }
}
