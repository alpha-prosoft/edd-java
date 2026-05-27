package com.alphaprosoft.edd.router;

import java.util.function.Supplier;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * {@link Sender} backed by SQS. CRaC/SnapStart-aware: the {@link SqsClient} is closed before
 * checkpoint and rebuilt after restore, mirroring {@code edd-java-aws}'s {@code SqsRouter}.
 */
public final class SqsSender implements Sender, Resource {

  private final Supplier<SqsClient> factory;
  private volatile SqsClient client;

  public SqsSender() {
    this(SqsClient::create);
  }

  public SqsSender(Supplier<SqsClient> factory) {
    this.factory = factory;
    this.client = factory.get();
    Core.getGlobalContext().register(this);
  }

  @Override
  public void send(String queueUrl, String body) {
    client.sendMessage(b -> b.queueUrl(queueUrl).messageBody(body));
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
