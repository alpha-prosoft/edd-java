package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.core.RequestMeta;

/**
 * Sends a produced effect (follow-up command) to the router service, which routes it to the proper
 * target service. All outputs go to the router; the runtime never targets services directly.
 */
@FunctionalInterface
public interface Router {
  void route(Command command, RequestMeta meta);
}
