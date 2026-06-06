package com.example.sample;

import com.alphaprosoft.edd.http.EddServer;
import com.alphaprosoft.edd.http.Tls;

/**
 * HTTP/2 server runtime: serves the Application at {@code POST /api/command} and {@code /api/query}
 * (plus {@code GET /health}). Run with {@code java -jar target/sample-server.jar}.
 */
public final class SampleServer {

  public static void main(String[] args) {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8443"));
    EddServer server = new EddServer(SampleApp.build(), port, Tls.serverContext());
    server.start();
    System.out.printf("%s listening on https://0.0.0.0:%d (HTTP/2)%n", SampleIds.SERVICE, port);
  }

  private SampleServer() {}
}
