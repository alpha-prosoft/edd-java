package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

public record GreetCustomerCommand(UUID id, UUID customerId) implements Command {}
