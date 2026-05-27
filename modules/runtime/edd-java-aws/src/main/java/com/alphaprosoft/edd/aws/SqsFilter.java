package com.alphaprosoft.edd.aws;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQS ingestion. Each record body is a {@code {cmdId,command,meta}} message; effects are routed by
 * the {@link Processor}. Returns the AWS standard partial-batch response ({@code
 * {batchItemFailures:[{itemIdentifier}]}}): only records that threw are reported as failures (and
 * redelivered) — a handled business {@code Rejection} is a success and is not retried.
 */
public final class SqsFilter implements IngestFilter {

  @Override
  public boolean handles(JsonNode event) {
    return "aws:sqs".equals(Messages.text(Messages.firstRecord(event), "eventSource"));
  }

  @Override
  public Object process(JsonNode event, Processor processor) {
    List<Map<String, String>> failures = new ArrayList<>();
    for (JsonNode record : event.get("Records")) {
      try {
        Inbound in = Messages.decodeCommand(Messages.parse(record.get("body").asText()));
        processor.handle(in.command(), in.meta());
      } catch (RuntimeException e) {
        failures.add(Map.of("itemIdentifier", Messages.text(record, "messageId")));
      }
    }
    return Map.of("batchItemFailures", failures);
  }
}
