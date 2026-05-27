package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.Rejection;
import java.util.List;

public final class IncrementHandler implements CommandHandler<IncrementCommand, CounterAggregate> {

  @Override
  public List<CommandEmission> handle(CommandContext<CounterAggregate> ctx, IncrementCommand cmd) {
    if (cmd.amount() <= 0) {
      return List.of(Rejection.of("non-positive"));
    }
    long priorCount = ctx.aggregate() == null ? 0 : ctx.aggregate().count();
    return List.of(new IncrementedEvent(cmd.id(), cmd.amount(), priorCount));
  }
}
