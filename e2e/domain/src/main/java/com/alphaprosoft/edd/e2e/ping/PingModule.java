package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.Schema;
import com.alphaprosoft.edd.e2e.pong.PongCommand;
import com.alphaprosoft.edd.e2e.pong.PongSetValueCommand;
import java.util.List;

/**
 * ping-svc registration. Effects cross the service boundary as plain commands: {@code pinged} emits
 * a {@code pong} (owned by pong-svc) until the hop guard, and {@code broadcasted} fans a value out
 * to one ping aggregate and one pong aggregate — the router addresses each by its globally-unique
 * cmdId.
 */
public final class PingModule {

  public static final long MAX_HOPS = 5;

  public static Module<PingAggregate> register() {
    return Module.builder(PingAggregate.class)
        .regCmd(PingIds.PING, spec -> spec.handler(PingHandler.class).build())
        .regCmd(PingIds.SET_VALUE, spec -> spec.handler(PingSetValueHandler.class).build())
        .regCmd(PingIds.BROADCAST, spec -> spec.handler(PingBroadcastHandler.class).build())
        .regCmd(PingIds.CLAIM_NAME, spec -> spec.handler(PingClaimNameHandler.class).build())
        .regCmd(
            PingIds.SET_SCORE,
            spec ->
                spec.handler(PingSetScoreHandler.class)
                    .consumes(
                        Schema.require(
                            (PingSetScoreCommand c) -> c.score() > 0, "score must be positive"))
                    .build())
        .regCmd(
            PingIds.OBJECT_UPLOADED, spec -> spec.handler(PingObjectUploadedHandler.class).build())
        .regApply(PingIds.PINGED, PingAggregate::pinged)
        .regApply(PingIds.VALUE_SET, PingAggregate::valueSet)
        .regApply(PingIds.BROADCASTED, PingAggregate::broadcasted)
        .regApply(PingIds.NAME_CLAIMED, PingAggregate::nameClaimed)
        .regApply(PingIds.SCORE_SET, PingAggregate::scoreSet)
        .regApply(PingIds.OBJECT_RECORDED, PingAggregate::objectRecorded)
        .regFx(
            PingIds.PINGED,
            (ctx, e) ->
                e.hops() < MAX_HOPS
                    ? List.of(PongCommand.builder().id(e.id()).hops(e.hops() + 1).build())
                    : List.of())
        .regFx(
            PingIds.BROADCASTED,
            (ctx, e) ->
                List.of(
                    PingSetValueCommand.builder().id(e.pingTarget()).value(e.value()).build(),
                    PongSetValueCommand.builder().id(e.pongTarget()).value(e.value()).build()))
        .regQuery(
            PingIds.GET_PING, (ctx, q) -> ctx.<PingAggregate>getAggregate(q.id()).orElse(null))
        .build();
  }

  private PingModule() {}
}
