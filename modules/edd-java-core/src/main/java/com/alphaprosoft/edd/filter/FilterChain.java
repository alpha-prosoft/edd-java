package com.alphaprosoft.edd.filter;

/** The continuation a {@link Filter} calls to pass control to the next filter (or the terminal). */
@FunctionalInterface
public interface FilterChain {
  Object proceed(EddRequest request);
}
