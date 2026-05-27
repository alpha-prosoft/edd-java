package com.alphaprosoft.edd.crossservice;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

public record GreetCommand(UUID id, UUID customerId) implements Command {}
