package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

public record CustomerGreetedEvent(UUID id, String greeting) implements Event {}
