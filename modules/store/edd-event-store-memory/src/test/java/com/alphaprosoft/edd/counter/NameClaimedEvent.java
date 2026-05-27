package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

public record NameClaimedEvent(UUID id, String name) implements Event {}
