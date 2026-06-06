package com.alphaprosoft.edd.core;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

/**
 * Configuration for {@link Retry}: how many attempts, which exceptions are retryable, how long to
 * back off between attempts, and an optional hook run before each retry. Built with {@link
 * #builder()}; defaults are 3 attempts, retry every {@link RuntimeException}, no backoff, no hook.
 */
public final class RetryConfig {

  private final int maxAttempts;
  private final Predicate<RuntimeException> retryOn;
  private final LongUnaryOperator backoffMillis;
  private final BiConsumer<Integer, RuntimeException> onRetry;

  private RetryConfig(Builder b) {
    if (b.maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1, was " + b.maxAttempts);
    }
    this.maxAttempts = b.maxAttempts;
    this.retryOn = Objects.requireNonNull(b.retryOn, "retryOn");
    this.backoffMillis = Objects.requireNonNull(b.backoffMillis, "backoffMillis");
    this.onRetry = Objects.requireNonNull(b.onRetry, "onRetry");
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public Predicate<RuntimeException> retryOn() {
    return retryOn;
  }

  /** Backoff before the next attempt, in millis, given the just-failed attempt number (1-based). */
  public LongUnaryOperator backoffMillis() {
    return backoffMillis;
  }

  /**
   * Called before each retry with the just-failed attempt number and the exception that triggered
   * it.
   */
  public BiConsumer<Integer, RuntimeException> onRetry() {
    return onRetry;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private int maxAttempts = 3;
    private Predicate<RuntimeException> retryOn = e -> true;
    private LongUnaryOperator backoffMillis = attempt -> 0L;
    private BiConsumer<Integer, RuntimeException> onRetry = (attempt, e) -> {};

    private Builder() {}

    /** Total attempts including the first (so {@code 3} means one try + two retries). */
    public Builder maxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    /** Retry only when this predicate accepts the thrown exception. */
    public Builder retryOn(Predicate<RuntimeException> retryOn) {
      this.retryOn = retryOn;
      return this;
    }

    /** Retry only on exceptions assignable to {@code type}. */
    public Builder retryOn(Class<? extends RuntimeException> type) {
      this.retryOn = type::isInstance;
      return this;
    }

    /**
     * Milliseconds to sleep before the next attempt, as a function of the just-failed attempt
     * (1-based).
     */
    public Builder backoffMillis(LongUnaryOperator backoffMillis) {
      this.backoffMillis = backoffMillis;
      return this;
    }

    /** Constant backoff between attempts. */
    public Builder backoffMillis(long millis) {
      this.backoffMillis = attempt -> millis;
      return this;
    }

    public Builder onRetry(BiConsumer<Integer, RuntimeException> onRetry) {
      this.onRetry = onRetry;
      return this;
    }

    public RetryConfig build() {
      return new RetryConfig(this);
    }
  }
}
