package com.alphaprosoft.edd.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandRouterTest {

  private record Sent(String queueUrl, String body) {}

  private static String sqsEvent(Map<String, String> bodyByMessageId) throws Exception {
    List<Object> records = new ArrayList<>();
    bodyByMessageId.forEach(
        (messageId, body) ->
            records.add(Map.of("eventSource", "aws:sqs", "messageId", messageId, "body", body)));
    return CommandRouter.MAPPER.writeValueAsString(Map.of("Records", records));
  }

  private static String message(String cmdId) throws Exception {
    return CommandRouter.MAPPER.writeValueAsString(
        Map.of("cmdId", cmdId, "command", Map.of("id", "x"), "meta", Map.of()));
  }

  @Test
  void routesKnownCmdToItsQueueAndFailsUnknown() throws Exception {
    List<Sent> sent = new ArrayList<>();
    CommandRouter router =
        CommandRouter.builder()
            .routes(Map.of("ping", "https://sqs/ping", "pong", "https://sqs/pong"))
            .sender((queueUrl, body) -> sent.add(new Sent(queueUrl, body)))
            .build();

    // LinkedHashMap-style ordering via two entries: one known, one unknown.
    Map<String, String> bodies = new java.util.LinkedHashMap<>();
    bodies.put("m-ping", message("ping"));
    bodies.put("m-unknown", message("nope"));

    String response = router.handle(sqsEvent(bodies));

    assertEquals(1, sent.size(), "only the known cmdId is forwarded");
    assertEquals("https://sqs/ping", sent.getFirst().queueUrl());
    assertTrue(sent.getFirst().body().contains("\"cmdId\":\"ping\""), "body forwarded verbatim");

    JsonNode failures = CommandRouter.MAPPER.readTree(response).get("batchItemFailures");
    assertEquals(1, failures.size(), "the unknown cmdId is reported for redelivery");
    assertEquals("m-unknown", failures.get(0).get("itemIdentifier").asText());
  }

  @Test
  void routesEveryRecordWhenAllKnown() throws Exception {
    List<Sent> sent = new ArrayList<>();
    CommandRouter router =
        CommandRouter.builder()
            .routes(Map.of("ping", "https://sqs/ping", "pong", "https://sqs/pong"))
            .sender((queueUrl, body) -> sent.add(new Sent(queueUrl, body)))
            .build();

    Map<String, String> bodies = new java.util.LinkedHashMap<>();
    bodies.put("m1", message("ping"));
    bodies.put("m2", message("pong"));

    String response = router.handle(sqsEvent(bodies));

    assertEquals(2, sent.size());
    assertEquals(0, CommandRouter.MAPPER.readTree(response).get("batchItemFailures").size());
  }
}
