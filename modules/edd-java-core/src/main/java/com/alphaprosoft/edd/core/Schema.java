package com.alphaprosoft.edd.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A lightweight validation contract — the Java-friendly stand-in for edd-core's malli {@code
 * :consumes}/{@code :produces}/aggregate-state schemas. {@link #violations(Object)} returns the
 * list of human-readable problems for a value; an empty list means valid.
 *
 * <p>Compose with {@link #and(Schema)} and build leaf rules with {@link #require(Predicate,
 * String)}.
 */
@FunctionalInterface
public interface Schema<T> {

  List<String> violations(T value);

  default boolean isValid(T value) {
    return violations(value).isEmpty();
  }

  default Schema<T> and(Schema<? super T> other) {
    return value -> {
      List<String> all = new ArrayList<>(this.violations(value));
      all.addAll(other.violations(value));
      return all;
    };
  }

  /** A single rule: {@code message} is reported when {@code predicate} does not hold. */
  static <T> Schema<T> require(Predicate<? super T> predicate, String message) {
    return value -> predicate.test(value) ? List.of() : List.of(message);
  }

  /** Rejects {@code null}, then applies {@code inner}. */
  static <T> Schema<T> notNull(String message) {
    return value -> value == null ? List.of(message) : List.of();
  }
}
