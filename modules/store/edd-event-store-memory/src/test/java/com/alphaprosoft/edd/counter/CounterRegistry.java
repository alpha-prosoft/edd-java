package com.alphaprosoft.edd.counter;

import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.EventId;

public final class CounterRegistry {

  public static final CommandId<IncrementCommand> INCREMENT =
      CommandId.of("increment", IncrementCommand.class);

  public static final CommandId<ClaimNameCommand> CLAIM_NAME =
      CommandId.of("claim-name", ClaimNameCommand.class);

  public static final EventId<IncrementedEvent> INCREMENTED =
      EventId.of("incremented", IncrementedEvent.class);

  public static final EventId<NameClaimedEvent> NAME_CLAIMED =
      EventId.of("name-claimed", NameClaimedEvent.class);

  private CounterRegistry() {}
}
