package com.alphaprosoft.edd.crossservice;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

public record GreetedEvent(UUID id, String greeting) implements Event {}
