package com.alphaprosoft.edd;

import java.util.List;

@FunctionalInterface
public interface EventFxHandler<E extends Event> {
    List<CommandEnvelope<?>> fx(Context ctx, E event);
}
