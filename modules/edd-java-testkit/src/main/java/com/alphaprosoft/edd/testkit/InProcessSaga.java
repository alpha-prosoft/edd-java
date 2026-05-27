package com.alphaprosoft.edd.testkit;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.RequestMeta;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Drives the full command → effects saga <em>synchronously, in-process</em> — the test analog of
 * edd-core's {@code with-mock-dal}/{@code execute-cmd}. The real AWS runtime does not do this (it
 * returns the response and sends effects to SQS); this runs each emitted effect command
 * immediately, advancing breadcrumbs per effect and aborting if the chain exceeds {@code
 * maxBreadcrumbDepth} (loop guard). Point it at an {@link Application} wired with in-memory stores
 * and assert on those.
 */
public final class InProcessSaga {

  private record Job(Command command, RequestMeta meta) {}

  private final Application app;
  private final int maxBreadcrumbDepth;

  public InProcessSaga(Application app) {
    this(app, 25);
  }

  public InProcessSaga(Application app, int maxBreadcrumbDepth) {
    this.app = app;
    this.maxBreadcrumbDepth = maxBreadcrumbDepth;
  }

  /**
   * Dispatch {@code command} and every effect it (transitively) produces; returns all responses.
   */
  public List<CommandResponse> run(Command command, RequestMeta meta) {
    List<CommandResponse> responses = new ArrayList<>();
    Deque<Job> queue = new ArrayDeque<>();
    queue.add(new Job(command, meta));
    while (!queue.isEmpty()) {
      Job job = queue.poll();
      if (job.meta().breadcrumbs().size() > maxBreadcrumbDepth) {
        throw new IllegalStateException("loop-detected: breadcrumbs " + job.meta().breadcrumbs());
      }
      CommandResponse response = app.dispatch(job.command(), job.meta());
      responses.add(response);
      if (response instanceof CommandResponse.Success success) {
        int index = 0;
        for (Command effect : success.effects()) {
          List<Integer> childCrumbs = new ArrayList<>(job.meta().breadcrumbs());
          childCrumbs.add(index++);
          queue.add(
              new Job(effect, RequestMeta.builder(job.meta()).breadcrumbs(childCrumbs).build()));
        }
      }
    }
    return responses;
  }

  /** The first (root command's) response. */
  public CommandResponse runFirst(Command command, RequestMeta meta) {
    return run(command, meta).getFirst();
  }
}
