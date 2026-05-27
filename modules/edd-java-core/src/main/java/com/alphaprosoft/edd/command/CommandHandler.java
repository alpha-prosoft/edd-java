package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.Aggregate;
import java.util.List;

@FunctionalInterface
public interface CommandHandler<C extends Command, A extends Aggregate> {
  List<CommandEmission> handle(CommandContext<A> ctx, C cmd);
}
