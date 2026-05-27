package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.query.QueryId;

/**
 * The ping-svc contract: service name + typed ids. cmdIds are globally unique so the router can
 * address them.
 */
public final class PingIds {

  public static final String SERVICE = "ping-svc";

  public static final CommandId<PingCommand> PING = CommandId.of("ping", PingCommand.class);
  public static final CommandId<PingSetValueCommand> SET_VALUE =
      CommandId.of("ping-set-value", PingSetValueCommand.class);
  public static final CommandId<PingBroadcastCommand> BROADCAST =
      CommandId.of("ping-broadcast", PingBroadcastCommand.class);
  public static final CommandId<PingClaimNameCommand> CLAIM_NAME =
      CommandId.of("ping-claim-name", PingClaimNameCommand.class);
  public static final CommandId<PingSetScoreCommand> SET_SCORE =
      CommandId.of("ping-set-score", PingSetScoreCommand.class);
  public static final CommandId<PingObjectUploadedCommand> OBJECT_UPLOADED =
      CommandId.of("ping-object-uploaded", PingObjectUploadedCommand.class);

  public static final EventId<PingedEvent> PINGED = EventId.of("pinged", PingedEvent.class);
  public static final EventId<PingValueSetEvent> VALUE_SET =
      EventId.of("ping-value-set", PingValueSetEvent.class);
  public static final EventId<BroadcastedEvent> BROADCASTED =
      EventId.of("ping-broadcasted", BroadcastedEvent.class);
  public static final EventId<NameClaimedEvent> NAME_CLAIMED =
      EventId.of("ping-name-claimed", NameClaimedEvent.class);
  public static final EventId<ScoreSetEvent> SCORE_SET =
      EventId.of("ping-score-set", ScoreSetEvent.class);
  public static final EventId<ObjectRecordedEvent> OBJECT_RECORDED =
      EventId.of("ping-object-recorded", ObjectRecordedEvent.class);

  public static final QueryId<GetPingQuery, PingAggregate> GET_PING =
      QueryId.of("get-ping", GetPingQuery.class, PingAggregate.class);

  /**
   * Force class-initialization so every id above is registered in the current JVM. A service that
   * <em>emits</em> these commands as cross-service effects (but doesn't handle them) must still
   * register their ids, because the SQS router encodes an effect by {@code CommandId.forType(...)}.
   */
  public static void touch() {}

  private PingIds() {}
}
