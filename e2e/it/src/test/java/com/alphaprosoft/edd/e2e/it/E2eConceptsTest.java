package com.alphaprosoft.edd.e2e.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration tests against the deployed E2E stack (real API Gateway + Lambdas + DynamoDB + S3 +
 * SQS). The deploy is done by run-e2e.sh, which exports {@code ApiUrl} and {@code
 * E2E_IMPORT_OBJECT_ID}. Each test exercises one CQRS concept end-to-end through the public API.
 */
class E2eConceptsTest {

  @Test
  void pingPongEffectLoop() {
    // A :ping bounces ping<->pong via the router (effects -> router queue -> service queues) and
    // stops at the hop guard (max 5). ping and pong share the aggregate id across their own stores.
    UUID id = UUID.randomUUID();

    assertTrue(Api.command("ping-svc", "ping", Map.of("id", id.toString(), "hops", 0)).success());

    long hops = Api.waitFor(n -> n.path("hops").asLong(), h -> h == 5, "pong-svc", "get-pong", id);
    assertEquals(5, hops, "ponged hops reached the guard via the router round-trips");
  }

  @Test
  void s3BucketFilter() {
    // A file uploaded to the import bucket becomes a ping-object-uploaded command (S3 ingestion).
    UUID objId = UUID.fromString(Api.required("E2E_IMPORT_OBJECT_ID"));

    String last =
        Api.waitFor(
            n -> n.path("last").asText(), "object-recorded"::equals, "ping-svc", "get-ping", objId);
    assertEquals("object-recorded", last);
  }

  @Test
  void crossServiceRemoteDependency() {
    // pong's :combine depends (over the API) on ping's aggregate value + version.
    UUID pingId = UUID.randomUUID();
    UUID pongId = UUID.randomUUID();

    assertTrue(
        Api.command(
                "ping-svc", "ping-set-value", Map.of("id", pingId.toString(), "value", "ping-v1"))
            .success());
    assertEquals(
        1L,
        (long)
            Api.waitFor(
                n -> n.path("version").asLong(), v -> v == 1, "ping-svc", "get-ping", pingId));

    assertTrue(
        Api.command(
                "pong-svc",
                "pong-combine",
                Map.of("id", pongId.toString(), "pingId", pingId.toString(), "value", "pong-v1"))
            .success());

    JsonNode pong =
        Api.waitFor(
            n -> n,
            n -> "ping-v1".equals(n.path("pingValue").asText()),
            "pong-svc",
            "get-pong",
            pongId);
    assertEquals("ping-v1", pong.path("pingValue").asText());
    assertEquals(1, pong.path("pingVersion").asLong());
    assertEquals("pong-v1", pong.path("value").asText());

    // updating ping bumps its version and a fresh combine reflects it
    assertTrue(
        Api.command("ping-svc", "ping-set-value", versioned(pingId, 1L, "ping-v2")).success());
    assertEquals(
        2L,
        (long)
            Api.waitFor(
                n -> n.path("version").asLong(), v -> v == 2, "ping-svc", "get-ping", pingId));

    assertTrue(
        Api.command(
                "pong-svc",
                "pong-combine",
                Map.of("id", pongId.toString(), "pingId", pingId.toString(), "value", "pong-v2"))
            .success());
    JsonNode pong2 =
        Api.waitFor(
            n -> n,
            n -> "ping-v2".equals(n.path("pingValue").asText()),
            "pong-svc",
            "get-pong",
            pongId);
    assertEquals(2, pong2.path("pingVersion").asLong());
    assertEquals("pong-v2", pong2.path("value").asText());
  }

  @Test
  void identityUniqueness() {
    UUID a1 = UUID.randomUUID();
    UUID a2 = UUID.randomUUID();
    String name = "alice-" + UUID.randomUUID();

    assertTrue(
        Api.command("ping-svc", "ping-claim-name", Map.of("id", a1.toString(), "name", name))
            .success());

    Api.Resp dup =
        Api.command("ping-svc", "ping-claim-name", Map.of("id", a2.toString(), "name", name));
    assertEquals(422, dup.status());
    assertEquals("identity-conflict", dup.code());

    assertTrue(
        Api.command("ping-svc", "ping-claim-name", Map.of("id", a2.toString(), "name", name + "-2"))
            .success());
  }

  @Test
  void commandValidation() {
    UUID id = UUID.randomUUID();

    assertTrue(
        Api.command("ping-svc", "ping-set-score", Map.of("id", id.toString(), "score", 42))
            .success());

    Api.Resp bad =
        Api.command("ping-svc", "ping-set-score", Map.of("id", id.toString(), "score", -5));
    assertEquals(422, bad.status());
    assertEquals("invalid-command", bad.code());
  }

  @Test
  void idempotency() {
    UUID id = UUID.randomUUID();
    UUID req = UUID.randomUUID();

    assertTrue(
        Api.command("ping-svc", "ping-set-value", Map.of("id", id.toString(), "value", "once"), req)
            .success());
    assertEquals(
        1L,
        (long)
            Api.waitFor(n -> n.path("version").asLong(), v -> v == 1, "ping-svc", "get-ping", id));

    // replaying the same request-id is deduplicated (no second event)
    Api.command("ping-svc", "ping-set-value", Map.of("id", id.toString(), "value", "twice"), req);
    sleep(3000);
    JsonNode agg = Api.aggregate("ping-svc", "get-ping", id);
    assertEquals(1, agg.path("version").asLong());
    assertEquals("once", agg.path("value").asText());
  }

  @Test
  void concurrentModification() {
    UUID id = UUID.randomUUID();

    assertTrue(
        Api.command("ping-svc", "ping-set-value", Map.of("id", id.toString(), "value", "v1"))
            .success());
    assertEquals(
        1L,
        (long)
            Api.waitFor(n -> n.path("version").asLong(), v -> v == 1, "ping-svc", "get-ping", id));

    assertTrue(Api.command("ping-svc", "ping-set-value", versioned(id, 1L, "v2")).success());
    assertEquals(
        2L,
        (long)
            Api.waitFor(n -> n.path("version").asLong(), v -> v == 2, "ping-svc", "get-ping", id));

    Api.Resp stale = Api.command("ping-svc", "ping-set-value", versioned(id, 1L, "v3"));
    assertEquals(422, stale.status());
    assertEquals("concurrent-modification", stale.code());

    JsonNode agg = Api.aggregate("ping-svc", "get-ping", id);
    assertEquals(2, agg.path("version").asLong());
    assertEquals("v2", agg.path("value").asText());

    assertTrue(Api.command("ping-svc", "ping-set-value", versioned(id, 2L, "v3")).success());
  }

  @Test
  void multiServiceFanOut() {
    // One event fans effects out to BOTH ping-svc and pong-svc via the router (two distinct
    // effects).
    UUID b = UUID.randomUUID();
    UUID pingTarget = UUID.randomUUID();
    UUID pongTarget = UUID.randomUUID();

    Map<String, Object> cmd = new LinkedHashMap<>();
    cmd.put("id", b.toString());
    cmd.put("pingTarget", pingTarget.toString());
    cmd.put("pongTarget", pongTarget.toString());
    cmd.put("value", "fanned");
    assertTrue(Api.command("ping-svc", "ping-broadcast", cmd).success());

    assertEquals(
        "fanned",
        Api.waitFor(
            n -> n.path("value").asText(), "fanned"::equals, "ping-svc", "get-ping", pingTarget));
    assertEquals(
        "fanned",
        Api.waitFor(
            n -> n.path("value").asText(), "fanned"::equals, "pong-svc", "get-pong", pongTarget));
  }

  private static Map<String, Object> versioned(UUID id, Long version, String value) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id.toString());
    m.put("version", version);
    m.put("value", value);
    return m;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
