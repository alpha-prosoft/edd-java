package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.e2e.ping.GetPingQuery;
import com.alphaprosoft.edd.e2e.ping.PingAggregate;
import com.alphaprosoft.edd.e2e.ping.PingIds;
import com.alphaprosoft.edd.query.Dep;

/**
 * Typed dependency keys for pong-svc. {@code PING} is resolved remotely from ping-svc over the
 * wire.
 */
public final class PongDeps {

  public static final Dep<GetPingQuery, PingAggregate> PING =
      Dep.remote("ping", PingIds.SERVICE, PingIds.GET_PING);

  private PongDeps() {}
}
