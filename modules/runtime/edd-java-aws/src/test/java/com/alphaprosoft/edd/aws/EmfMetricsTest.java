package com.alphaprosoft.edd.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.core.Tracer;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmfMetricsTest {

  @Test
  void emitsEmbeddedMetricFormatLine() throws Exception {
    StringBuilder out = new StringBuilder();
    new EmfMetrics("edd", out).count("edd.command", 3, Map.of("service", "note-svc"));

    JsonNode doc = Messages.MAPPER.readTree(out.toString().trim());
    assertEquals(3, doc.get("edd.command").asInt());
    assertEquals("note-svc", doc.get("service").asText());
    JsonNode directive = doc.get("_aws").get("CloudWatchMetrics").get(0);
    assertEquals("edd", directive.get("Namespace").asText());
    assertEquals("edd.command", directive.get("Metrics").get(0).get("Name").asText());
    assertEquals("Count", directive.get("Metrics").get(0).get("Unit").asText());
    assertEquals("service", directive.get("Dimensions").get(0).get(0).asText());
  }

  @Test
  void durationUsesMillisecondsUnit() throws Exception {
    StringBuilder out = new StringBuilder();
    new EmfMetrics("edd", out).duration("edd.dispatch.ms", 12, Map.of());
    JsonNode doc = Messages.MAPPER.readTree(out.toString().trim());
    assertEquals(
        "Milliseconds",
        doc.get("_aws").get("CloudWatchMetrics").get(0).get("Metrics").get(0).get("Unit").asText());
  }

  @Test
  void xRayTracerIsSafeWithoutAnActiveSegment() {
    Tracer tracer = new XRayTracer();
    // no Lambda segment in a unit test — must not throw, just no-op
    try (Tracer.Span span = tracer.span("edd.dispatch")) {
      span.error(new RuntimeException("boom"));
    }
    assertTrue(true);
  }
}
