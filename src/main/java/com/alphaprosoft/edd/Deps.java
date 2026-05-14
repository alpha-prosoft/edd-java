package com.alphaprosoft.edd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class Deps<C extends Command> {

    private final List<Registered<C, ?, ?>> registered;

    private Deps(List<Registered<C, ?, ?>> registered) {
        this.registered = List.copyOf(registered);
    }

    public static <C extends Command> Builder<C> builder() {
        return new Builder<>();
    }

    public static <C extends Command> Deps<C> empty() {
        return new Deps<>(List.of());
    }

    public List<Registered<C, ?, ?>> all() {
        return registered;
    }

    public record Registered<C extends Command, Q extends Query, T>(
            Dep<Q, T> key, BiFunction<Context, ? super C, Q> queryFn) {}

    public static final class Builder<C extends Command> {

        private final List<Registered<C, ?, ?>> registered = new ArrayList<>();

        private Builder() {}

        public <Q extends Query, T> Builder<C> reg(Dep<Q, T> key, BiFunction<Context, ? super C, Q> queryFn) {
            registered.add(new Registered<>(key, queryFn));
            return this;
        }

        public Deps<C> build() {
            return new Deps<>(registered);
        }
    }
}
