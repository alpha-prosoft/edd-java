package com.example.sample;

import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.query.QueryId;

/** The service contract: name plus typed ids, referenced by handlers, tests, and callers. */
public final class SampleIds {

  public static final String SERVICE = "sample-svc";

  public static final CommandId<CreateSampleCommand> CREATE =
      CommandId.of("create-sample", CreateSampleCommand.class);

  public static final EventId<SampleCreatedEvent> CREATED =
      EventId.of("sample-created", SampleCreatedEvent.class);

  public static final QueryId<GetSampleQuery, SampleAggregate> GET =
      QueryId.of("get-sample", GetSampleQuery.class, SampleAggregate.class);

  private SampleIds() {}
}
