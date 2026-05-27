package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.core.RequestMeta;
import java.util.function.Supplier;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * {@link Router} that sends effects to the router service's SQS queue. CRaC/SnapStart-aware: the
 * {@link SqsClient} is closed before checkpoint and rebuilt after restore.
 */
public final class SqsRouter implements Router, Resource {

  private final String routerQueueUrl;
  private final Supplier<SqsClient> factory;
  private volatile SqsClient client;

  public SqsRouter(String routerQueueUrl) {
    this(routerQueueUrl, SqsClient::create);
  }

  public SqsRouter(String routerQueueUrl, Supplier<SqsClient> factory) {
    this.routerQueueUrl = routerQueueUrl;
    this.factory = factory;
    this.client = factory.get();
    Core.getGlobalContext().register(this);
  }

  @Override
  public void route(Command command, RequestMeta meta) {
    String body = Messages.encodeCommand(command, meta);
    client.sendMessage(b -> b.queueUrl(routerQueueUrl).messageBody(body));
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) {
    client = factory.get();
  }
}
