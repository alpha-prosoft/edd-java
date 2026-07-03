package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Context;
import com.alphaprosoft.edd.query.QueryContext;

/**
 * The {@link Context} a command handler receives. Adds access to the current replayed aggregate
 * state, as edd-core does via {@code (el-ctx/get-aggregate ctx)}; view-store reads of the command's
 * own aggregate type need no type token.
 */
public interface CommandContext<A extends Aggregate> extends QueryContext<A> {

  /**
   * Current aggregate state for the command's id, or {@code null} if none is loaded. Populated once
   * the event store / replay lands; until then handlers read prior state through a dependency
   * query.
   */
  A aggregate();
}
