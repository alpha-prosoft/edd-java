package com.alphaprosoft.edd.eventstore.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.command.CommandEmission;
import com.alphaprosoft.edd.command.CommandHandler;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.command.Identity;
import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.core.OptimisticLockException;
import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.core.RetryConfig;
import com.alphaprosoft.edd.core.StoredEvent;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The dispatcher retries {@code concurrent-modification} against a fresh cache, up to the
 * configured attempts.
 */
class ConcurrentModificationRetryTest {

  record Bump(UUID id) implements Command {}

  record Bumped(UUID id) implements Event {}

  record Ctr(UUID id, long version, long n) implements Aggregate {
    static Ctr bumped(Ctr a, Bumped e) {
      return new Ctr(e.id(), 0, (a == null ? 0 : a.n()) + 1);
    }
  }

  static final CommandId<Bump> BUMP = CommandId.of("retry-bump", Bump.class);
  static final EventId<Bumped> BUMPED = EventId.of("retry-bumped", Bumped.class);

  public static final class BumpHandler implements CommandHandler<Bump, Ctr> {
    static final AtomicInteger RUNS = new AtomicInteger();

    public BumpHandler() {}

    @Override
    public List<CommandEmission> handle(CommandContext<Ctr> ctx, Bump cmd) {
      RUNS.incrementAndGet();
      return List.of(new Bumped(cmd.id()));
    }
  }

  /**
   * Wraps a real store but throws {@link OptimisticLockException} on the first {@code throwTimes}
   * appends.
   */
  static final class FlakyStore implements EventStore {
    private final EventStore delegate;
    private final int throwTimes;
    final AtomicInteger appendCalls = new AtomicInteger();

    FlakyStore(EventStore delegate, int throwTimes) {
      this.delegate = delegate;
      this.throwTimes = throwTimes;
    }

    @Override
    public void appendBatch(
        String realm, String service, List<StoredEvent> events, List<Identity> identities) {
      if (appendCalls.incrementAndGet() <= throwTimes) {
        throw new OptimisticLockException(
            events.isEmpty() ? UUID.randomUUID() : events.getFirst().aggregateId(), 1);
      }
      delegate.appendBatch(realm, service, events, identities);
    }

    @Override
    public List<StoredEvent> load(String realm, UUID id) {
      return delegate.load(realm, id);
    }

    @Override
    public List<StoredEvent> load(String realm, UUID id, long afterSeq) {
      return delegate.load(realm, id, afterSeq);
    }

    @Override
    public long maxEventSeq(String realm, UUID id) {
      return delegate.maxEventSeq(realm, id);
    }

    @Override
    public void append(String realm, UUID id, long expectedVersion, List<StoredEvent> events) {
      delegate.append(realm, id, expectedVersion, events);
    }

    @Override
    public List<StoredEvent> eventsByInteraction(String realm, UUID interactionId) {
      return delegate.eventsByInteraction(realm, interactionId);
    }

    @Override
    public void reserveIdentities(String realm, String service, List<Identity> identities) {
      delegate.reserveIdentities(realm, service, identities);
    }

    @Override
    public Optional<UUID> aggregateIdByIdentity(String realm, String service, String name) {
      return delegate.aggregateIdByIdentity(realm, service, name);
    }

    @Override
    public Map<String, UUID> aggregateIdByIdentity(
        String realm, String service, Collection<String> names) {
      return delegate.aggregateIdByIdentity(realm, service, names);
    }

    @Override
    public void logRequest(String realm, UUID requestId, List<Integer> breadcrumbs, Object body) {
      delegate.logRequest(realm, requestId, breadcrumbs, body);
    }

    @Override
    public void logResponse(
        String realm, UUID requestId, List<Integer> breadcrumbs, CommandResponse response) {
      delegate.logResponse(realm, requestId, breadcrumbs, response);
    }

    @Override
    public void logError(String realm, UUID requestId, List<Integer> breadcrumbs, Object error) {
      delegate.logError(realm, requestId, breadcrumbs, error);
    }

    @Override
    public Optional<CommandResponse> findResponse(
        String realm, UUID requestId, List<Integer> breadcrumbs) {
      return delegate.findResponse(realm, requestId, breadcrumbs);
    }
  }

  private static Application app(FlakyStore store, RetryConfig retry) {
    Application.Builder b =
        Application.builder("retry-svc")
            .eventStore(store)
            .viewStore(InMemoryViewStore.builder().build())
            .module(
                Module.builder(Ctr.class)
                    .regCmd(BUMP, spec -> spec.handler(BumpHandler.class).build())
                    .regApply(BUMPED, Ctr::bumped)
                    .build());
    if (retry != null) {
      b.retryConfig(retry);
    }
    return b.build();
  }

  @Test
  void retriesConcurrentModificationThenSucceeds() {
    BumpHandler.RUNS.set(0);
    FlakyStore store = new FlakyStore(InMemoryEventStore.builder().build(), 1); // throw once
    Application app = app(store, null); // default: 3 attempts, no backoff

    CommandResponse resp = app.dispatch(new Bump(UUID.randomUUID()), RequestMeta.newRequest());

    assertInstanceOf(CommandResponse.Success.class, resp, "succeeds after one retry");
    assertEquals(2, store.appendCalls.get(), "committed on the second attempt");
    assertEquals(2, BumpHandler.RUNS.get(), "handler re-ran against fresh state on retry");
  }

  @Test
  void failsWithConcurrentModificationAfterExhaustingRetries() {
    BumpHandler.RUNS.set(0);
    FlakyStore store = new FlakyStore(InMemoryEventStore.builder().build(), 99); // always throw
    Application app =
        app(
            store,
            RetryConfig.builder().maxAttempts(3).retryOn(OptimisticLockException.class).build());

    CommandResponse resp = app.dispatch(new Bump(UUID.randomUUID()), RequestMeta.newRequest());

    CommandResponse.Failure f = assertInstanceOf(CommandResponse.Failure.class, resp);
    assertEquals("concurrent-modification", f.code());
    assertEquals(3, store.appendCalls.get(), "tried exactly maxAttempts times");
    assertTrue(BumpHandler.RUNS.get() == 3, "handler ran once per attempt");
  }
}
