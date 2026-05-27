package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.core.Metrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Metrics} that writes the CloudWatch <b>Embedded Metric Format</b> — one JSON line per
 * metric to an {@link Appendable} (stdout in Lambda). CloudWatch extracts the metric from the log
 * line; no SDK call, no extra latency. The dimension map's keys become EMF dimensions and its
 * entries become properties on the document.
 */
public final class EmfMetrics implements Metrics {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private final String namespace;
  private final Appendable out;

  public EmfMetrics() {
    this("edd", System.out);
  }

  public EmfMetrics(String namespace) {
    this(namespace, System.out);
  }

  EmfMetrics(String namespace, Appendable out) {
    this.namespace = namespace;
    this.out = out;
  }

  @Override
  public void count(String name, long delta, Map<String, String> dimensions) {
    emit(name, delta, "Count", dimensions);
  }

  @Override
  public void duration(String name, long millis, Map<String, String> dimensions) {
    emit(name, millis, "Milliseconds", dimensions);
  }

  private void emit(String name, long value, String unit, Map<String, String> dimensions) {
    Map<String, Object> doc = new LinkedHashMap<>();
    Map<String, Object> metricDef = new LinkedHashMap<>();
    metricDef.put("Name", name);
    metricDef.put("Unit", unit);
    Map<String, Object> directive = new LinkedHashMap<>();
    directive.put("Namespace", namespace);
    directive.put("Dimensions", List.of(new ArrayList<>(dimensions.keySet())));
    directive.put("Metrics", List.of(metricDef));
    Map<String, Object> aws = new LinkedHashMap<>();
    aws.put("Timestamp", System.currentTimeMillis());
    aws.put("CloudWatchMetrics", List.of(directive));
    doc.put("_aws", aws);
    doc.putAll(dimensions);
    doc.put(name, value);
    try {
      out.append(MAPPER.writeValueAsString(doc)).append('\n');
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
