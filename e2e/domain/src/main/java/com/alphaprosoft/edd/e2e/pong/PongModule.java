package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.e2e.ping.GetPingQuery;
import com.alphaprosoft.edd.e2e.ping.PingCommand;
import java.util.List;

/**
 * pong-svc registration. {@code ponged} emits a {@code ping} (owned by ping-svc) until the hop
 * guard, mirroring ping. {@code combine} reads ping's aggregate through a remote dep ({@link
 * PongDeps#PING}), resolved over the wire by the Application's {@code RemoteServiceClient}.
 */
public final class PongModule {

  public static final long MAX_HOPS = 5;

  public static Module<PongAggregate> register() {
    return Module.builder(PongAggregate.class)
        .regCmd(PongIds.PONG, spec -> spec.handler(PongHandler.class).build())
        .regCmd(PongIds.SET_VALUE, spec -> spec.handler(PongSetValueHandler.class).build())
        .regCmd(
            PongIds.COMBINE,
            spec ->
                spec.handler(PongCombineHandler.class)
                    .dep(
                        PongDeps.PING,
                        (ctx, cmd) -> GetPingQuery.builder().id(cmd.pingId()).build())
                    .build())
        .regApply(PongIds.PONGED, PongAggregate::ponged)
        .regApply(PongIds.VALUE_SET, PongAggregate::valueSet)
        .regApply(PongIds.COMBINED, PongAggregate::combined)
        .regFx(
            PongIds.PONGED,
            (ctx, e) ->
                e.hops() < MAX_HOPS
                    ? List.of(PingCommand.builder().id(e.id()).hops(e.hops() + 1).build())
                    : List.of())
        .regQuery(
            PongIds.GET_PONG, (ctx, q) -> ctx.<PongAggregate>getAggregate(q.id()).orElse(null))
        .build();
  }

  private PongModule() {}
}
