package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.core.Tracer;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.strategy.LogErrorContextMissingStrategy;

/**
 * {@link Tracer} backed by AWS X-Ray: each span is an X-Ray <b>subsegment</b> nested under the
 * Lambda invocation's segment. Outside a traced context (local/tests) the recorder is set to log
 * rather than throw, so the same wiring is safe everywhere — wired via {@code
 * Application.builder().tracer(new XRayTracer())}.
 */
public final class XRayTracer implements Tracer {

  public XRayTracer() {
    AWSXRay.getGlobalRecorder().setContextMissingStrategy(new LogErrorContextMissingStrategy());
  }

  @Override
  public Span span(String name) {
    final Subsegment subsegment;
    try {
      subsegment = AWSXRay.beginSubsegment(name);
    } catch (RuntimeException contextMissing) {
      return Span.NOOP;
    }
    return new Span() {
      @Override
      public void error(Throwable t) {
        if (subsegment != null) {
          subsegment.addException(t);
        }
      }

      @Override
      public void close() {
        try {
          AWSXRay.endSubsegment();
        } catch (RuntimeException ignored) {
          // no active context — nothing to end
        }
      }
    };
  }
}
