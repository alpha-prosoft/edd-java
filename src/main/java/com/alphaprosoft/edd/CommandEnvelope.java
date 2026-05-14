package com.alphaprosoft.edd;

import java.util.Optional;

public record CommandEnvelope<C extends Command>(Optional<Service> service, C command) {

    public static <C extends Command> CommandEnvelope<C> local(C command) {
        return new CommandEnvelope<>(Optional.empty(), command);
    }

    public static <C extends Command> CommandEnvelope<C> on(Service service, C command) {
        return new CommandEnvelope<>(Optional.of(service), command);
    }
}
