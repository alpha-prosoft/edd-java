package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** {@code priorCount} captures the replayed state the handler saw, so tests can assert replay. */
public record IncrementedEvent(UUID id, long amount, long priorCount) implements Event {}
