package com.alphaprosoft.edd.command;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Context;

/**
 * The {@link Context} a command handler receives. Adds access to the current replayed aggregate
 * state, as edd-core does via {@code (el-ctx/get-aggregate ctx)}.
 */
public interface CommandContext<A extends Aggregate> extends Context {

  /**
   * Current aggregate state for the command's id, or {@code null} if none is loaded. Populated once
   * the event store / replay lands; until then handlers read prior state through a dependency
   * query.
   */
  A aggregate();
}
