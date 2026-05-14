package com.alphaprosoft.edd;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface CommandResponse {

    record Success(UUID aggregateId, List<Event> events, List<CommandEnvelope<?>> effects) implements CommandResponse {
        public Success {
            events = List.copyOf(events);
            effects = List.copyOf(effects);
        }
    }

    record Failure(String code, Map<String, Object> details) implements CommandResponse {
        public Failure {
            details = Map.copyOf(details);
        }
    }
}
