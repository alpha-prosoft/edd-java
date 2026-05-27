package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.query.QueryId;

/** The pong-svc contract: service name + typed ids. */
public final class PongIds {

  public static final String SERVICE = "pong-svc";

  public static final CommandId<PongCommand> PONG = CommandId.of("pong", PongCommand.class);
  public static final CommandId<PongSetValueCommand> SET_VALUE =
      CommandId.of("pong-set-value", PongSetValueCommand.class);
  public static final CommandId<PongCombineCommand> COMBINE =
      CommandId.of("pong-combine", PongCombineCommand.class);

  public static final EventId<PongedEvent> PONGED = EventId.of("ponged", PongedEvent.class);
  public static final EventId<PongValueSetEvent> VALUE_SET =
      EventId.of("pong-value-set", PongValueSetEvent.class);
  public static final EventId<CombinedEvent> COMBINED =
      EventId.of("pong-combined", CombinedEvent.class);

  public static final QueryId<GetPongQuery, PongAggregate> GET_PONG =
      QueryId.of("get-pong", GetPongQuery.class, PongAggregate.class);

  /**
   * Force class-initialization so every id above is registered in the current JVM. A service that
   * <em>emits</em> these commands as cross-service effects (but doesn't handle them) must still
   * register their ids, because the SQS router encodes an effect by {@code CommandId.forType(...)}.
   */
  public static void touch() {}

  private PongIds() {}
}
