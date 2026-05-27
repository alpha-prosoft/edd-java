package com.alphaprosoft.edd.router;

/** Sends a message body to a queue. Abstracts SQS so the router core stays testable. */
@FunctionalInterface
public interface Sender {
  void send(String queueUrl, String body);
}
