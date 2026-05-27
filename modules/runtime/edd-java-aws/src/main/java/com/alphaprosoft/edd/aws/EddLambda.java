package com.alphaprosoft.edd.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lambda entry point for an edd service. A service subclasses this and builds its {@link
 * LambdaRuntime} in {@link #configure()} — which runs once at init, so AWS SnapStart (CRaC)
 * checkpoints a fully-wired application for near-instant restore. Works on the managed Java
 * runtime; no custom runtime loop.
 */
public abstract class EddLambda implements RequestStreamHandler {

  private final LambdaRuntime runtime;

  protected EddLambda() {
    this.runtime = configure();
  }

  protected abstract LambdaRuntime configure();

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    runtime.handle(input, output);
  }
}
