package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.core.RemoteServiceClient;
import com.alphaprosoft.edd.demo.customer.CustomerIds;
import com.alphaprosoft.edd.eventstore.memory.InMemoryEventStore;
import com.alphaprosoft.edd.http.EddServer;
import com.alphaprosoft.edd.http.HttpServiceClient;
import com.alphaprosoft.edd.http.Tls;
import com.alphaprosoft.edd.viewstore.memory.InMemoryViewStore;
import java.util.Map;

public final class GreeterMain {

  public static void main(String[] args) {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8443"));
    String customerUrl =
        System.getenv().getOrDefault("CUSTOMER_SVC_URL", "https://customer-svc:8443");

    RemoteServiceClient remote = new HttpServiceClient(Map.of(CustomerIds.SERVICE, customerUrl));
    EddServer server =
        new EddServer(
            GreeterApp.build(
                remote, InMemoryEventStore.builder().build(), InMemoryViewStore.builder().build()),
            port,
            Tls.serverContext());
    server.start();
    System.out.printf(
        "%s listening on https://0.0.0.0:%d (HTTP/2 only), customer-svc at %s%n",
        GreeterIds.SERVICE, port, customerUrl);
  }

  private GreeterMain() {}
}
