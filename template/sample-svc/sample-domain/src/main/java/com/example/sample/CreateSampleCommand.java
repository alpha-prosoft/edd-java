package com.example.sample;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/** Imperative request to create a sample. cmdId {@code create-sample}. */
public record CreateSampleCommand(UUID id, String name) implements Command {}
