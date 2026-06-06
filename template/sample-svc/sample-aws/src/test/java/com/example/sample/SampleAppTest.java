package com.example.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the App wiring runs end to end on in-memory stores, without live DynamoDB or S3. */
class SampleAppTest {

  @Test
  void wiringRunsOnInMemoryStores() {
    Application app =
        SampleApp.build(
            InMemoryEventStore.builder().build(), InMemoryViewStore.builder().build());
    UUID id = UUID.randomUUID();

    CommandResponse response =
        app.dispatch(new CreateSampleCommand(id, "hello"), RequestMeta.newRequest());
    assertInstanceOf(CommandResponse.Success.class, response);

    SampleAggregate aggregate =
        app.query(SampleIds.GET, new GetSampleQuery(id), RequestMeta.newRequest());
    assertEquals("hello", aggregate.name());
  }
}
