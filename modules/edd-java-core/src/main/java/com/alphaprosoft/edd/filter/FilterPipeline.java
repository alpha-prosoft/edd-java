package com.alphaprosoft.edd.filter;

import java.util.List;

/**
 * Composes {@link Filter}s around a terminal step. {@code filters.get(0)} runs first; the terminal
 * (typically the dispatch into an {@code Application}) runs last. Mirrors edd-core's {@code
 * apply-filters} reduce, but as an explicit servlet-style chain.
 */
public final class FilterPipeline {

  private final List<Filter> filters;
  private final FilterChain terminal;

  public FilterPipeline(List<Filter> filters, FilterChain terminal) {
    this.filters = List.copyOf(filters);
    this.terminal = terminal;
  }

  public Object run(EddRequest request) {
    FilterChain chain = terminal;
    for (int i = filters.size() - 1; i >= 0; i--) {
      Filter filter = filters.get(i);
      FilterChain next = chain;
      chain = req -> filter.doFilter(req, next);
    }
    return chain.proceed(request);
  }
}
