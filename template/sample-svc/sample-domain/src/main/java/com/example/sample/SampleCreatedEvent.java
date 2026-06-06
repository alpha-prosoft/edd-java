package com.example.sample;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** Past-tense fact: a sample was created. eventId {@code sample-created}. */
public record SampleCreatedEvent(UUID id, String name) implements Event {}
