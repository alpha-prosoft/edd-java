package com.alphaprosoft.edd;

import java.util.List;
import java.util.Map;

public sealed interface HandlerResult<A extends Aggregate> {

    record Events<A extends Aggregate>(List<Event> events) implements HandlerResult<A> {
        public Events {
            events = List.copyOf(events);
        }
    }

    record Error<A extends Aggregate>(String code, Map<String, Object> details) implements HandlerResult<A> {
        public Error {
            details = Map.copyOf(details);
        }
    }

    static <A extends Aggregate> HandlerResult<A> of(Event event) {
        return new Events<>(List.of(event));
    }

    static <A extends Aggregate> HandlerResult<A> of(Event... events) {
        return new Events<>(List.of(events));
    }

    static <A extends Aggregate> HandlerResult<A> error(String code) {
        return new Error<>(code, Map.of());
    }

    static <A extends Aggregate> HandlerResult<A> error(String code, Map<String, Object> details) {
        return new Error<>(code, details);
    }
}
