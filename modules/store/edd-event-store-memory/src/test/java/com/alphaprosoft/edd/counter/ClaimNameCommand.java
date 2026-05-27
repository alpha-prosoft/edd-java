package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

public record ClaimNameCommand(UUID id, String name) implements Command {}
