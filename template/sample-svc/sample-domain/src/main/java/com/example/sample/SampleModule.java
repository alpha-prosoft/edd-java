package com.example.sample;

import com.alphaprosoft.edd.core.Module;

/** Wires the command handler, the event apply, and the read query for the sample aggregate. */
public final class SampleModule {

  public static Module<SampleAggregate> register() {
    return Module.builder(SampleAggregate.class)
        .regCmd(SampleIds.CREATE, spec -> spec.handler(CreateSampleHandler.class).build())
        .regApply(SampleIds.CREATED, SampleAggregate::created)
        .regQuery(
            SampleIds.GET,
            (ctx, query) -> ctx.getAggregate(query.id()).orElse(null))
        .build();
  }

  private SampleModule() {}
}
