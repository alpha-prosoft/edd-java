package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.core.Context;
import java.util.function.BiFunction;

/**
 * One dependency of a command or query: a {@link Dep} key plus a function that builds the query to
 * resolve it from the resolved {@link Context} and the input ({@code I} = the command or query).
 * Shared by command and query specs so a single resolver handles both — local or remote.
 */
public record DepBinding<I, Q extends Query, T>(
    Dep<Q, T> key, BiFunction<Context, ? super I, Q> queryFn) {}
