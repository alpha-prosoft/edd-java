package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.RequestMeta;

/**
 * What a {@link IngestFilter} calls to handle one decoded command: dispatch it and send any effects
 * to the router. Returns the {@link CommandResponse} (a fresh one, or — on a duplicate — the stored
 * one, whose effects are routed again). The runtime supplies the implementation.
 */
@FunctionalInterface
public interface Processor {
  CommandResponse handle(Command command, RequestMeta meta);
}
