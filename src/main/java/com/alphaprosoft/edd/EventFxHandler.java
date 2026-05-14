package com.alphaprosoft.edd;

import java.util.List;

@FunctionalInterface
public interface EventFxHandler<E extends Event> {
    List<Command> fx(Context ctx, E event);
}
