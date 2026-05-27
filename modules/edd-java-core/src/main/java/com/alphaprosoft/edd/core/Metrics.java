package com.alphaprosoft.edd.core;

import java.util.Map;

/**
 * A metrics seam for custom application metrics, kept in core so the engine can emit
 * counters/timers without an AWS dependency. The on-prem default is {@link #NONE} (no-op); the AWS
 * module supplies an EMF (CloudWatch embedded-metric) implementation. Services emit their own
 * metrics through the same interface, obtained from the {@code Context} or held alongside the
 * {@code Application}.
 */
public interface Metrics {

  /** Add {@code delta} to a counter, tagged with {@code dimensions}. */
  void count(String name, long delta, Map<String, String> dimensions);

  /** Record a duration in milliseconds, tagged with {@code dimensions}. */
  void duration(String name, long millis, Map<String, String> dimensions);

  Metrics NONE =
      new Metrics() {
        @Override
        public void count(String name, long delta, Map<String, String> dimensions) {}

        @Override
        public void duration(String name, long millis, Map<String, String> dimensions) {}
      };
}
