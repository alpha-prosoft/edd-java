package com.alphaprosoft.edd.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.Metrics;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.core.Telemetry;
import com.alphaprosoft.edd.core.Tracer;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class TelemetryTest {

  record AddNote(UUID id, String text) implements Command {}

  record NoteAdded(UUID id, String text) implements Event {}

  record Note(UUID id, long version) implements Aggregate {
    static Note added(Note n, NoteAdded e) {
      return new Note(e.id(), 0);
    }
  }

  public static final class AddNoteHandler implements CommandHandler<AddNote, Note> {
    @Override
    public List<CommandEmission> handle(CommandContext<Note> ctx, AddNote cmd) {
      return List.of(new NoteAdded(cmd.id(), cmd.text()));
    }
  }

  static final CommandId<AddNote> ADD_NOTE = CommandId.of("note-add", AddNote.class);
  static final EventId<NoteAdded> NOTE_ADDED = EventId.of("note-added", NoteAdded.class);

  private record Captured(String event, Map<String, Object> fields) {}

  @Test
  void emitsLifecycleEventsWithCorrelation() {
    List<Captured> captured = new CopyOnWriteArrayList<>();
    Telemetry capture = (event, fields) -> captured.add(new Captured(event, fields));

    Application app =
        Application.builder("note-svc")
            .telemetry(capture)
            .eventStore(InMemoryEventStore.builder().build())
            .viewStore(InMemoryViewStore.builder().build())
            .module(
                Module.builder(Note.class)
                    .regCmd(ADD_NOTE, spec -> spec.handler(AddNoteHandler.class).build())
                    .regApply(NOTE_ADDED, Note::added)
                    .build())
            .build();

    RequestMeta meta = RequestMeta.builder().requestId(UUID.randomUUID()).build();
    app.dispatch(new AddNote(UUID.randomUUID(), "hi"), meta);

    List<String> names = captured.stream().map(Captured::event).toList();
    assertTrue(names.contains("command.received"), names.toString());
    assertTrue(names.contains("command.succeeded"), names.toString());

    Captured succeeded =
        captured.stream()
            .filter(c -> c.event().equals("command.succeeded"))
            .findFirst()
            .orElseThrow();
    assertEquals("note-svc", succeeded.fields().get("service"));
    assertEquals(meta.requestId().toString(), succeeded.fields().get("requestId"));
    assertEquals(1, succeeded.fields().get("events"));
  }

  @Test
  void emitsDeduplicatedOnReplay() {
    List<String> events = new CopyOnWriteArrayList<>();
    Application app =
        Application.builder("note-svc")
            .telemetry((event, fields) -> events.add(event))
            .eventStore(InMemoryEventStore.builder().build())
            .viewStore(InMemoryViewStore.builder().build())
            .module(
                Module.builder(Note.class)
                    .regCmd(ADD_NOTE, spec -> spec.handler(AddNoteHandler.class).build())
                    .regApply(NOTE_ADDED, Note::added)
                    .build())
            .build();

    RequestMeta meta = RequestMeta.builder().requestId(UUID.randomUUID()).build();
    AddNote cmd = new AddNote(UUID.randomUUID(), "hi");
    app.dispatch(cmd, meta);
    app.dispatch(cmd, meta); // same request-id + breadcrumbs

    assertTrue(events.contains("command.deduplicated"), events.toString());
  }

  @Test
  void tracerAndMetricsAreInvokedOnDispatch() {
    List<String> spans = new CopyOnWriteArrayList<>();
    List<String> closed = new CopyOnWriteArrayList<>();
    Tracer tracer =
        name -> {
          spans.add(name);
          return new Tracer.Span() {
            @Override
            public void close() {
              closed.add(name);
            }
          };
        };
    List<String> counters = new CopyOnWriteArrayList<>();
    List<String> durations = new CopyOnWriteArrayList<>();
    Metrics metrics =
        new Metrics() {
          @Override
          public void count(String name, long delta, Map<String, String> dimensions) {
            counters.add(name + "=" + delta);
          }

          @Override
          public void duration(String name, long millis, Map<String, String> dimensions) {
            durations.add(name);
          }
        };

    Application app =
        Application.builder("note-svc")
            .tracer(tracer)
            .metrics(metrics)
            .eventStore(InMemoryEventStore.builder().build())
            .viewStore(InMemoryViewStore.builder().build())
            .module(
                Module.builder(Note.class)
                    .regCmd(ADD_NOTE, spec -> spec.handler(AddNoteHandler.class).build())
                    .regApply(NOTE_ADDED, Note::added)
                    .build())
            .build();

    app.dispatch(new AddNote(UUID.randomUUID(), "hi"), RequestMeta.newRequest());

    assertTrue(spans.contains("edd.dispatch"), spans.toString());
    assertTrue(closed.contains("edd.dispatch"), "span closed");
    assertTrue(counters.contains("edd.command=1"), counters.toString());
    assertTrue(durations.contains("edd.dispatch.ms"), durations.toString());
  }
}
