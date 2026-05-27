package com.alphaprosoft.edd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alphaprosoft.edd.query.Dep;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Store-free engine behaviour: query path, context metadata, query deps, build validation. */
class EngineQueryTest {

  record Person(UUID id, String name) {}

  record GetPerson(UUID id) implements Query {}

  record GetGreeting(UUID id) implements Query {}

  static final QueryId<GetPerson, Person> GET_PERSON =
      QueryId.of("get-person", GetPerson.class, Person.class);
  static final QueryId<GetGreeting, String> GET_GREETING =
      QueryId.of("get-greeting", GetGreeting.class, String.class);
  static final Dep<GetPerson, Person> PERSON = Dep.of("person", GET_PERSON);

  @Test
  void contextExposesRealmUserAndAnnotations() {
    AtomicReference<Context> seen = new AtomicReference<>();
    Application app =
        Application.builder("people-svc")
            .regQuery(
                GET_PERSON,
                (ctx, q) -> {
                  seen.set(ctx);
                  return new Person(q.id(), "Ada");
                })
            .build();

    User user = User.of("u-1", "reader");
    RequestMeta meta =
        RequestMeta.builder().realm("tenant-a").user(user).annotation("source", "test").build();
    app.query(GET_PERSON, new GetPerson(UUID.randomUUID()), meta);

    assertEquals("tenant-a", seen.get().realm());
    assertSame(user, seen.get().user());
    assertEquals("test", seen.get().annotations().get("source"));
  }

  @Test
  void queryResolvesItsDeps() {
    UUID id = UUID.randomUUID();
    Application app =
        Application.builder("people-svc")
            .regQuery(GET_PERSON, (ctx, q) -> new Person(q.id(), "Ada"))
            .regQuery(
                GET_GREETING,
                spec ->
                    spec.handler((ctx, q) -> "Hi " + ctx.getDeps(PERSON).name())
                        .dep(PERSON, (ctx, q) -> new GetPerson(q.id()))
                        .build())
            .build();

    assertEquals("Hi Ada", app.query(GET_GREETING, new GetGreeting(id), RequestMeta.newRequest()));
  }

  @Test
  void unresolvedDepFailsAtBuild() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                Application.builder("people-svc")
                    .regQuery(
                        GET_GREETING,
                        spec ->
                            spec.handler((ctx, q) -> "x")
                                .dep(PERSON, (ctx, q) -> new GetPerson(q.id()))
                                .build())
                    .build());
    assertEquals(true, ex.getMessage().contains("get-person"));
  }
}
