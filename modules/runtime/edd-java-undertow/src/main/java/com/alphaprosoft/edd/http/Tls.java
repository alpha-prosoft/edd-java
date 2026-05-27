package com.alphaprosoft.edd.http;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * TLS helpers for the demo. HTTP/2 is negotiated over TLS via ALPN, so a cert is required. The
 * server loads a bundled self-signed PKCS12 keystore; clients trust any cert (demo only).
 */
public final class Tls {

  private static final String KEYSTORE = "/edd-demo-keystore.p12";
  private static final char[] PASSWORD = "changeit".toCharArray();

  private Tls() {}

  public static SSLContext serverContext() {
    try (InputStream in = Tls.class.getResourceAsStream(KEYSTORE)) {
      if (in == null) {
        throw new IllegalStateException("Missing keystore on classpath: " + KEYSTORE);
      }
      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(in, PASSWORD);
      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(ks, PASSWORD);
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
      return ctx;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build server SSL context", e);
    }
  }

  /** Trust-all context for the demo client — the self-signed cert is not in any truststore. */
  public static SSLContext trustAllContext() {
    try {
      TrustManager[] trustAll = {
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        }
      };
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, trustAll, new SecureRandom());
      return ctx;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build trust-all SSL context", e);
    }
  }
}
