package com.alphaprosoft.edd.router;

import com.alphaprosoft.edd.core.config.Config;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lambda entry point for the router. The routing table is read from {@code router.routes} (env
 * {@code EDD_ROUTER_ROUTES}, a JSON {@code cmdId -> queueUrl} object) once at init, so AWS
 * SnapStart (CRaC) checkpoints a fully-wired router for near-instant restore. Handler: {@code
 * com.alphaprosoft.edd.router.RouterLambda::handleRequest}.
 */
public final class RouterLambda implements RequestStreamHandler {

  private final CommandRouter router = CommandRouter.fromConfig(Config.load());

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    router.handle(input, output);
  }
}
