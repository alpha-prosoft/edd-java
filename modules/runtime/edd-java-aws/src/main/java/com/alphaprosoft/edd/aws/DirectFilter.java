package com.alphaprosoft.edd.aws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Direct invocation: the payload is a single {@code {cmdId,command,meta}} message. Returns the
 * response summary.
 */
public final class DirectFilter implements IngestFilter {

  @Override
  public boolean handles(JsonNode event) {
    return event.hasNonNull("cmdId");
  }

  @Override
  public Object process(JsonNode event, Processor processor) {
    Inbound in = Messages.decodeCommand(event);
    return Messages.summary(processor.handle(in.command(), in.meta()));
  }
}
