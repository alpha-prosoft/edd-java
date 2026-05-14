package com.alphaprosoft.edd;

@FunctionalInterface
public interface CommandHandler<C extends Command, A extends Aggregate> {
    HandlerResult<A> handle(Context ctx, C cmd);
}
