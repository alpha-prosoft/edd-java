package com.alphaprosoft.edd.core;

/**
 * A tracing seam (edd-core's X-Ray integration), kept in core so the engine can open a span around
 * a dispatch/query without depending on any AWS library. The on-prem default is {@link #NONE}
 * (no-op); the AWS module supplies an X-Ray-backed implementation, wired via {@code
 * Application.builder().tracer(...)}.
 */
@FunctionalInterface
public interface Tracer {

  /** Begin a span; close it (try-with-resources) when the traced work finishes. */
  Span span(String name);

  Tracer NONE = name -> Span.NOOP;

  /** An open span. {@link #error(Throwable)} marks it faulted; {@link #close()} ends it. */
  interface Span extends AutoCloseable {

    Span NOOP =
        new Span() {
          @Override
          public void error(Throwable t) {}

          @Override
          public void close() {}
        };

    default void error(Throwable t) {}

    @Override
    void close();
  }
}
