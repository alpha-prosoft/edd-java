package com.alphaprosoft.edd.core;

import java.util.function.Supplier;

/**
 * A small generic retry helper: run an action, and if it throws a {@link RuntimeException} the
 * {@link RetryConfig} deems retryable, run it again — up to {@code maxAttempts}, with the
 * configured backoff between attempts. The last exception is rethrown once attempts are exhausted
 * (or immediately for a non-retryable exception). Used by the dispatcher to retry {@code
 * concurrent-modification} against freshly replayed aggregates, but it is fully generic — {@code
 * Retry.retry(config, action)}.
 */
public final class Retry {

  private Retry() {}

  public static <T> T retry(RetryConfig config, Supplier<T> action) {
    for (int attempt = 1; ; attempt++) {
      try {
        return action.get();
      } catch (RuntimeException e) {
        if (attempt >= config.maxAttempts() || !config.retryOn().test(e)) {
          throw e;
        }
        config.onRetry().accept(attempt, e);
        long sleep = config.backoffMillis().applyAsLong(attempt);
        if (sleep > 0) {
          try {
            Thread.sleep(sleep);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw e;
          }
        }
      }
    }
  }

  /** Convenience for actions that return nothing. */
  public static void retry(RetryConfig config, Runnable action) {
    retry(
        config,
        () -> {
          action.run();
          return null;
        });
  }
}
