package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.aws.EddLambda;
import com.alphaprosoft.edd.aws.EmfMetrics;
import com.alphaprosoft.edd.aws.LambdaRuntime;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.e2e.pong.PongIds;
import com.alphaprosoft.edd.eventstore.dynamodb.DynamoDbEventStore;
import com.alphaprosoft.edd.viewstore.s3.S3ViewStore;
import java.util.UUID;

/**
 * ping-svc Lambda. Handler: {@code com.alphaprosoft.edd.e2e.ping.PingLambda::handleRequest}. Wired
 * once at init (SnapStart checkpoints it): DynamoDB event store + S3 view store, ping module, and a
 * runtime that ingests API/SQS/S3 and sends effects to the router queue ({@code
 * EDD_ROUTER_QUEUE_URL}).
 */
public final class PingLambda extends EddLambda {

  @Override
  protected LambdaRuntime configure() {
    // register pong's command ids too: ping emits pong commands as effects and the router encodes
    // them by CommandId.forType, which requires the id to be registered in this JVM.
    PongIds.touch();
    Config config = Config.load();
    // The view store derives its service partition from the app's serviceName() — no manual wiring,
    // so ping and pong can never collide on a shared aggregates bucket.
    Application app =
        Application.builder(PingIds.SERVICE)
            .config(config)
            .eventStore(DynamoDbEventStore::fromConfig)
            .viewStore(S3ViewStore::fromConfig)
            .metrics(new EmfMetrics())
            .module(PingModule::register)
            .build();
    // S3 ingestion before SQS: the import bucket delivers S3 notifications wrapped in SQS, which
    // the S3 filter unwraps; plain command messages on the commands queue fall through to SQS.
    return LambdaRuntime.builder(app)
        .api()
        .s3(PingLambda::objectUploaded)
        .sqs()
        .router(config.get("router.queue.url", ""))
        .build();
  }

  /** The uploaded object's aggregate id is the UUID filename ({@code …/<id>.json}). */
  private static PingObjectUploadedCommand objectUploaded(String bucket, String key) {
    String file = key.substring(key.lastIndexOf('/') + 1);
    String id = file.endsWith(".json") ? file.substring(0, file.length() - ".json".length()) : file;
    return PingObjectUploadedCommand.builder()
        .id(UUID.fromString(id))
        .bucket(bucket)
        .key(key)
        .build();
  }
}
