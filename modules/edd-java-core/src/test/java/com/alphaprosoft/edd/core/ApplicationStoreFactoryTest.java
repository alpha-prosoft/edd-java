package com.alphaprosoft.edd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The store-factory wiring: a {@code (app, config)} factory is invoked at {@link
 * Application.Builder#build()} with the fully-identified app, so a store derives its service from
 * {@link Application#serviceName()} rather than being told it separately.
 */
class ApplicationStoreFactoryTest {

  private static ViewStore stubViewStore() {
    return new ViewStore() {
      @Override
      public void update(String realm, Aggregate aggregate) {}

      @Override
      public <A extends Aggregate> Optional<A> getSnapshot(String realm, UUID aggregateId) {
        return Optional.empty();
      }

      @Override
      public <A extends Aggregate> Optional<A> getSnapshot(
          String realm, UUID aggregateId, long version) {
        return Optional.empty();
      }
    };
  }

  @Test
  void factoryReceivesServiceNameAndResolvesStore() {
    AtomicReference<String> seenService = new AtomicReference<>();
    ViewStore stub = stubViewStore();
    Application app =
        Application.builder("svc-x")
            .viewStore(
                (a, c) -> {
                  seenService.set(a.serviceName());
                  return stub;
                })
            .build();
    assertEquals("svc-x", seenService.get(), "factory must see the app's service name");
    assertSame(stub, app.viewStore(), "the factory's store is the app's store");
  }

  @Test
  void factoryGetsExplicitConfig() {
    Config config = Config.builder().build();
    AtomicReference<Config> seen = new AtomicReference<>();
    Application.builder("svc-x")
        .config(config)
        .viewStore(
            (a, c) -> {
              seen.set(c);
              return stubViewStore();
            })
        .build();
    assertSame(config, seen.get(), "the configured Config is handed to the factory");
  }

  @Test
  void factoryGetsLoadedConfigWhenNoneGiven() {
    AtomicReference<Config> seen = new AtomicReference<>();
    Application.builder("svc-x")
        .viewStore(
            (a, c) -> {
              seen.set(c);
              return stubViewStore();
            })
        .build();
    assertNotNull(
        seen.get(), "config defaults to Config.load() when a factory is used without one");
  }

  @Test
  void instanceStoreStillWorks() {
    ViewStore stub = stubViewStore();
    Application app = Application.builder("svc-x").viewStore(stub).build();
    assertSame(stub, app.viewStore());
  }

  public record Thing(UUID id, long version) implements Aggregate {}

  public record GetThing() implements Query {}

  static final QueryId<GetThing, String> GET_THING =
      QueryId.of("get-thing", GetThing.class, String.class);

  @Test
  void moduleFactoryReceivesAppAndItsRegistrationsTakeEffect() {
    AtomicReference<String> seenService = new AtomicReference<>();
    Application app =
        Application.builder("svc-mod")
            .viewStore(stubViewStore())
            .module(
                (a, c) -> {
                  seenService.set(a.serviceName());
                  return Module.builder(Thing.class).regQuery(GET_THING, (ctx, q) -> "ok").build();
                })
            .build();
    assertEquals("svc-mod", seenService.get(), "module factory must see the app's service name");
    assertEquals(
        "ok",
        app.query(GET_THING, new GetThing(), RequestMeta.newRequest()),
        "a deferred module's registrations are live on the built app");
  }
}
