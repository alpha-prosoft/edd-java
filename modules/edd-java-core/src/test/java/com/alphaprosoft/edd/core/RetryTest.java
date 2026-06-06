package com.alphaprosoft.edd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

  static final class Boom extends RuntimeException {}

  static final class Nope extends RuntimeException {}

  @Test
  void returnsFirstTryWithoutRetrying() {
    AtomicInteger calls = new AtomicInteger();
    String r =
        Retry.retry(
            RetryConfig.builder().build(),
            () -> {
              calls.incrementAndGet();
              return "ok";
            });
    assertEquals("ok", r);
    assertEquals(1, calls.get());
  }

  @Test
  void retriesThenSucceeds() {
    AtomicInteger calls = new AtomicInteger();
    String r =
        Retry.retry(
            RetryConfig.builder().maxAttempts(3).retryOn(Boom.class).build(),
            () -> {
              if (calls.incrementAndGet() < 3) {
                throw new Boom();
              }
              return "ok";
            });
    assertEquals("ok", r);
    assertEquals(3, calls.get(), "tried until the third attempt succeeded");
  }

  @Test
  void rethrowsAfterExhaustingAttempts() {
    AtomicInteger calls = new AtomicInteger();
    assertThrows(
        Boom.class,
        () ->
            Retry.retry(
                RetryConfig.builder().maxAttempts(3).retryOn(Boom.class).build(),
                () -> {
                  calls.incrementAndGet();
                  throw new Boom();
                }));
    assertEquals(3, calls.get(), "exactly maxAttempts tries");
  }

  @Test
  void doesNotRetryNonMatchingException() {
    AtomicInteger calls = new AtomicInteger();
    assertThrows(
        Nope.class,
        () ->
            Retry.retry(
                RetryConfig.builder().maxAttempts(5).retryOn(Boom.class).build(),
                () -> {
                  calls.incrementAndGet();
                  throw new Nope();
                }));
    assertEquals(1, calls.get(), "non-retryable exception rethrown immediately");
  }

  @Test
  void invokesOnRetryHookAndBackoffPerFailedAttempt() {
    List<Integer> retried = new ArrayList<>();
    List<Long> backoffAttempts = new ArrayList<>();
    assertThrows(
        Boom.class,
        () ->
            Retry.retry(
                RetryConfig.builder()
                    .maxAttempts(3)
                    .retryOn(Boom.class)
                    .onRetry((attempt, e) -> retried.add(attempt))
                    .backoffMillis(
                        attempt -> {
                          backoffAttempts.add(attempt);
                          return 0L;
                        })
                    .build(),
                () -> {
                  throw new Boom();
                }));
    assertEquals(List.of(1, 2), retried, "hook fires before each of the 2 retries");
    assertEquals(List.of(1L, 2L), backoffAttempts, "backoff computed for the 2 failed attempts");
  }

  @Test
  void runnableOverloadRetries() {
    AtomicInteger calls = new AtomicInteger();
    Retry.retry(
        RetryConfig.builder().maxAttempts(2).retryOn(Boom.class).build(),
        () -> {
          if (calls.incrementAndGet() < 2) {
            throw new Boom();
          }
        });
    assertEquals(2, calls.get());
  }

  @Test
  void rejectsZeroAttempts() {
    assertThrows(
        IllegalArgumentException.class, () -> RetryConfig.builder().maxAttempts(0).build());
  }

  @Test
  void retryOnPredicateForm() {
    // a value-based predicate (not just by type) also works
    AtomicInteger calls = new AtomicInteger();
    String r =
        Retry.retry(
            RetryConfig.builder()
                .maxAttempts(4)
                .retryOn(e -> e.getMessage() != null && e.getMessage().equals("again"))
                .build(),
            () -> {
              if (calls.incrementAndGet() < 2) {
                throw new IllegalStateException("again");
              }
              return "done";
            });
    assertSame("done", r);
    assertEquals(2, calls.get());
  }
}
