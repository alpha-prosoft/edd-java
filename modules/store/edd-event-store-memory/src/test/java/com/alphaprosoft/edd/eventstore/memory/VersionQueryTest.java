package com.alphaprosoft.edd.eventstore.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Version-in-query parity with edd-core's {@code get-by-id}: a query carrying a version returns the
 * historical aggregate at that version; without one it returns the latest.
 */
class VersionQueryTest {

  record SetVal(UUID id, String val) implements Command {}

  record ValSet(UUID id, String val) implements Event {}

  record Doc(UUID id, long version, String val) implements Aggregate {
    static Doc valSet(Doc a, ValSet e) {
      return new Doc(e.id(), 0, e.val());
    }
  }

  /** A query that supports an optional version (null => latest). */
  record GetDoc(UUID id, Long version) implements Query {}

  static final CommandId<SetVal> SET_VAL = CommandId.of("ver-set-val", SetVal.class);
  static final EventId<ValSet> VAL_SET = EventId.of("ver-val-set", ValSet.class);
  static final QueryId<GetDoc, String> GET_DOC =
      QueryId.of("ver-get-doc", GetDoc.class, String.class);

  public static final class SetValHandler implements CommandHandler<SetVal, Doc> {
    public SetValHandler() {}

    @Override
    public List<CommandEmission> handle(CommandContext<Doc> ctx, SetVal cmd) {
      return List.of(new ValSet(cmd.id(), cmd.val()));
    }
  }

  private static Application app() {
    return Application.builder("ver-svc")
        .eventStore(InMemoryEventStore.builder().build())
        .viewStore(InMemoryViewStore.builder().build())
        .module(
            Module.builder(Doc.class)
                .regCmd(SET_VAL, spec -> spec.handler(SetValHandler.class).build())
                .regApply(VAL_SET, Doc::valSet)
                .regQuery(
                    GET_DOC,
                    (ctx, q) -> {
                      var agg =
                          q.version() == null
                              ? ctx.<Doc>getAggregate(q.id())
                              : ctx.<Doc>getAggregate(q.id(), q.version());
                      return agg.map(Doc::val).orElse(null);
                    })
                .build())
        .build();
  }

  @Test
  void queryWithVersionReturnsHistoricalValueElseLatest() {
    Application app = app();
    UUID id = UUID.randomUUID();

    app.dispatch(new SetVal(id, "a"), RequestMeta.newRequest()); // -> version 1
    app.dispatch(new SetVal(id, "b"), RequestMeta.newRequest()); // -> version 2

    assertEquals(
        "a",
        app.query(GET_DOC, new GetDoc(id, 1L), RequestMeta.newRequest()),
        "version 1 returns the historical value");
    assertEquals(
        "b",
        app.query(GET_DOC, new GetDoc(id, 2L), RequestMeta.newRequest()),
        "version 2 returns the historical value");
    assertEquals(
        "b",
        app.query(GET_DOC, new GetDoc(id, null), RequestMeta.newRequest()),
        "no version returns the latest");
    assertNull(
        app.query(GET_DOC, new GetDoc(id, 99L), RequestMeta.newRequest()),
        "never-stored version is absent");
  }
}
