package com.alphaprosoft.edd.demo.customer;

import com.alphaprosoft.edd.http.EddServer;
import com.alphaprosoft.edd.http.Tls;

public final class CustomerMain {

  public static void main(String[] args) {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8443"));
    EddServer server = new EddServer(CustomerApp.build(), port, Tls.serverContext());
    server.start();
    System.out.printf(
        "%s listening on https://0.0.0.0:%d (HTTP/2 only)%n", CustomerIds.SERVICE, port);
  }

  private CustomerMain() {}
}
