package com.alphaprosoft.edd.filter;

/**
 * A servlet-style request filter: inspect/transform the {@link EddRequest}, then call {@code
 * chain.proceed(request)} to continue — or return early to short-circuit (e.g. an idempotency
 * filter returning a cached response without dispatching). Filters wrap one another in registration
 * order, with the dispatch as the terminal step.
 */
@FunctionalInterface
public interface Filter {
  Object doFilter(EddRequest request, FilterChain chain);
}
