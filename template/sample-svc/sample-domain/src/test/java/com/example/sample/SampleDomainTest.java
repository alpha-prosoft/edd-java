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

/** Domain test over in-memory stores — exercises command, event, and query without external infra. */
class SampleDomainTest {

  private static Application inMemory() {
    return Application.builder(SampleIds.SERVICE)
        .eventStore(InMemoryEventStore.builder().build())
        .viewStore(InMemoryViewStore.builder().build())
        .module(SampleModule::register)
        .build();
  }

  @Test
  void createDispatchesAndProjects() {
    Application app = inMemory();
    UUID id = UUID.randomUUID();

    CommandResponse response =
        app.dispatch(new CreateSampleCommand(id, "hello"), RequestMeta.newRequest());
    assertInstanceOf(CommandResponse.Success.class, response);

    SampleAggregate aggregate =
        app.query(SampleIds.GET, new GetSampleQuery(id), RequestMeta.newRequest());
    assertEquals("hello", aggregate.name());
    assertEquals(1, aggregate.revision());
  }
}
