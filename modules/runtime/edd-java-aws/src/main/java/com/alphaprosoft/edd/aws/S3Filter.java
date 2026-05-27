package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.core.RequestMeta;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S3 ingestion, both shapes:
 *
 * <ul>
 *   <li><b>Direct</b> S3 → Lambda: {@code Records[].eventSource == aws:s3}. No partial-batch
 *       protocol, so a failure propagates (S3/EventBridge retries).
 *   <li><b>S3 → SQS → Lambda</b>: each SQS record's {@code body} is an S3 notification (the common
 *       decoupled pattern, and what an import queue delivers). The body is unwrapped and each inner
 *       S3 object handled; the standard SQS partial-batch response is returned so only failed
 *       records are redelivered. The one-time {@code s3:TestEvent} S3 sends when wiring a
 *       notification is acked.
 * </ul>
 *
 * Each S3 object (bucket + key) is turned into a command by the configured {@link S3CommandMapper}.
 */
public final class S3Filter implements IngestFilter {

  private final S3CommandMapper mapper;
  private final String realm;

  public S3Filter(S3CommandMapper mapper) {
    this(mapper, RequestMeta.DEFAULT_REALM);
  }

  public S3Filter(S3CommandMapper mapper, String realm) {
    this.mapper = mapper;
    this.realm = realm;
  }

  @Override
  public boolean handles(JsonNode event) {
    JsonNode first = Messages.firstRecord(event);
    String source = Messages.text(first, "eventSource");
    if ("aws:s3".equals(source)) {
      return true;
    }
    if ("aws:sqs".equals(source) && first.hasNonNull("body")) {
      JsonNode body = tryParse(first.get("body").asText());
      return body != null
          && ("s3:TestEvent".equals(Messages.text(body, "Event"))
              || "aws:s3".equals(Messages.text(Messages.firstRecord(body), "eventSource")));
    }
    return false;
  }

  @Override
  public Object process(JsonNode event, Processor processor) {
    boolean viaSqs = "aws:sqs".equals(Messages.text(Messages.firstRecord(event), "eventSource"));
    if (!viaSqs) {
      int processed = 0;
      for (JsonNode record : event.get("Records")) {
        handleObject(record, processor);
        processed++;
      }
      return Map.of("status", "ok", "processed", processed);
    }
    List<Map<String, String>> failures = new ArrayList<>();
    for (JsonNode record : event.get("Records")) {
      try {
        JsonNode body = Messages.parse(record.get("body").asText());
        if ("s3:TestEvent".equals(Messages.text(body, "Event"))) {
          continue; // S3's wiring probe — ack and ignore
        }
        JsonNode inner = body.get("Records");
        if (inner != null) {
          for (JsonNode s3Record : inner) {
            handleObject(s3Record, processor);
          }
        }
      } catch (RuntimeException e) {
        failures.add(Map.of("itemIdentifier", Messages.text(record, "messageId")));
      }
    }
    return Map.of("batchItemFailures", failures);
  }

  private void handleObject(JsonNode s3Record, Processor processor) {
    JsonNode s3 = s3Record.get("s3");
    String bucket = s3.get("bucket").get("name").asText();
    String key = URLDecoder.decode(s3.get("object").get("key").asText(), StandardCharsets.UTF_8);
    processor.handle(mapper.map(bucket, key), RequestMeta.builder().realm(realm).build());
  }

  private static JsonNode tryParse(String json) {
    try {
      return Messages.MAPPER.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }
}
