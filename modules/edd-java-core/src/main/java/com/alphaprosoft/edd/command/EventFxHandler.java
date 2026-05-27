package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.Context;
import java.util.List;

@FunctionalInterface
public interface EventFxHandler<E extends Event> {
  List<Command> fx(Context ctx, E event);
}
