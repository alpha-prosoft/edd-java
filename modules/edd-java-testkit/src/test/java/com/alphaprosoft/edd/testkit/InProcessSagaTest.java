package com.alphaprosoft.edd.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InProcessSagaTest {

  record PingCommand(UUID id) implements Command {}

  record PongCommand(UUID id) implements Command {}

  record PingedEvent(UUID id) implements Event {}

  record PongedEvent(UUID id) implements Event {}

  record SagaAggregate(UUID id, long version) implements Aggregate {
    static SagaAggregate pinged(SagaAggregate a, PingedEvent e) {
      return new SagaAggregate(e.id(), 0);
    }

    static SagaAggregate ponged(SagaAggregate a, PongedEvent e) {
      return new SagaAggregate(e.id(), 0);
    }
  }

  public static final class PingHandler implements CommandHandler<PingCommand, SagaAggregate> {
    @Override
    public List<CommandEmission> handle(CommandContext<SagaAggregate> ctx, PingCommand cmd) {
      return List.of(new PingedEvent(cmd.id()));
    }
  }

  public static final class PongHandler implements CommandHandler<PongCommand, SagaAggregate> {
    @Override
    public List<CommandEmission> handle(CommandContext<SagaAggregate> ctx, PongCommand cmd) {
      return List.of(new PongedEvent(cmd.id()));
    }
  }

  static final CommandId<PingCommand> PING = CommandId.of("ping", PingCommand.class);
  static final CommandId<PongCommand> PONG = CommandId.of("pong", PongCommand.class);
  static final EventId<PingedEvent> PINGED = EventId.of("pinged", PingedEvent.class);
  static final EventId<PongedEvent> PONGED = EventId.of("ponged", PongedEvent.class);

  @Test
  void runsEffectChainSynchronously() {
    InMemoryEventStore events = InMemoryEventStore.builder().build();
    Application app =
        Application.builder("saga-svc")
            .eventStore(events)
            .viewStore(InMemoryViewStore.builder().build())
            .module(
                Module.builder(SagaAggregate.class)
                    .regCmd(PING, spec -> spec.handler(PingHandler.class).build())
                    .regCmd(PONG, spec -> spec.handler(PongHandler.class).build())
                    .regApply(PINGED, SagaAggregate::pinged)
                    .regApply(PONGED, SagaAggregate::ponged)
                    // an emitted PingedEvent triggers a follow-up Pong command
                    .regFx(PINGED, (ctx, e) -> List.of(new PongCommand(UUID.randomUUID())))
                    .build())
            .build();

    UUID pingId = UUID.randomUUID();
    List<CommandResponse> responses =
        new InProcessSaga(app).run(new PingCommand(pingId), RequestMeta.newRequest());

    assertEquals(2, responses.size(), "ping + its follow-up pong both ran");
    var ping = assertInstanceOf(CommandResponse.Success.class, responses.get(0));
    assertInstanceOf(PingedEvent.class, ping.events().getFirst());
    assertEquals(1, ping.effects().size());
    var pong = assertInstanceOf(CommandResponse.Success.class, responses.get(1));
    assertInstanceOf(PongedEvent.class, pong.events().getFirst());
    assertEquals(0, pong.effects().size());

    // both events were persisted (on their respective aggregates)
    assertEquals(1, events.load(RequestMeta.DEFAULT_REALM, pingId).size());
  }
}
