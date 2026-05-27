package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.aws.EddLambda;
import com.alphaprosoft.edd.aws.EmfMetrics;
import com.alphaprosoft.edd.aws.LambdaRuntime;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.RemoteServiceClient;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.e2e.ping.PingIds;
import com.alphaprosoft.edd.eventstore.dynamodb.DynamoDbEventStore;
import com.alphaprosoft.edd.http.HttpServiceClient;
import com.alphaprosoft.edd.viewstore.s3.S3ViewStore;

/**
 * pong-svc Lambda. Like ping-svc, but also wires a {@link RemoteServiceClient} so {@code combine}
 * can read ping's aggregate over the wire. The peer is reached through API Gateway at {@code
 * <EDD_API_URL>/<service>} — REST API Gateway may answer over HTTP/1.1, so the client is built with
 * {@code requireHttp2(false)}.
 */
public final class PongLambda extends EddLambda {

  @Override
  protected LambdaRuntime configure() {
    // register ping's command ids too: pong emits ping commands as effects and the router encodes
    // them by CommandId.forType, which requires the id to be registered in this JVM.
    PingIds.touch();
    Config config = Config.load();
    String apiUrl = config.get("api.url", "");
    RemoteServiceClient remote =
        HttpServiceClient.builder()
            .urlResolver((service, realm) -> apiUrl + "/" + service)
            .requireHttp2(false)
            .build();
    Application app =
        Application.builder(PongIds.SERVICE)
            .config(config)
            .eventStore(DynamoDbEventStore::fromConfig)
            .viewStore(S3ViewStore::fromConfig)
            .remoteClient(remote)
            .metrics(new EmfMetrics())
            .module(PongModule::register)
            .build();
    return LambdaRuntime.builder(app)
        .api()
        .sqs()
        .router(config.get("router.queue.url", ""))
        .build();
  }
}
