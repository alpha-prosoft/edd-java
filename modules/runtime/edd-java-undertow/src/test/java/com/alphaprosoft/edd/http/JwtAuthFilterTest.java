package com.alphaprosoft.edd.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.filter.EddRequest;
import com.alphaprosoft.edd.filter.FilterChain;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtAuthFilterTest {

  private static final byte[] SECRET =
      "test-secret-test-secret-test!".getBytes(StandardCharsets.UTF_8);

  private static String token(byte[] secret, String payloadJson) throws Exception {
    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    String header = b64.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
    String payload = b64.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    String sig =
        b64.encodeToString(
            mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));
    return header + "." + payload + "." + sig;
  }

  private static EddRequest withToken(String jwt) {
    EddRequest req = new EddRequest(null, RequestMeta.newRequest());
    req.attributes().put(JwtAuthFilter.TOKEN_ATTRIBUTE, jwt);
    return req;
  }

  @Test
  void mapsClaimsOntoRequestMeta() throws Exception {
    String jwt =
        token(
            SECRET,
            "{\"sub\":\"u-1\",\"email\":\"ada@x.io\",\"realm\":\"tenant-a\","
                + "\"cognito:groups\":[\"order-admin\",\"reader\"]}");
    FilterChain terminal = req -> req.meta();

    RequestMeta meta = (RequestMeta) new JwtAuthFilter(SECRET).doFilter(withToken(jwt), terminal);

    assertEquals("tenant-a", meta.realm());
    assertEquals("u-1", meta.user().id());
    assertEquals("order-admin", meta.user().role());
    assertTrue(meta.user().roles().contains("reader"));
  }

  @Test
  void rejectsBadSignature() throws Exception {
    String jwt =
        token(
            "wrong-secret-wrong-secret-wrong".getBytes(StandardCharsets.UTF_8), "{\"sub\":\"u\"}");
    FilterChain terminal = req -> req.meta();
    assertThrows(
        IllegalStateException.class,
        () -> new JwtAuthFilter(SECRET).doFilter(withToken(jwt), terminal));
  }

  @Test
  void noTokenLeavesMetaUntouched() {
    FilterChain terminal = req -> req.meta();
    RequestMeta meta =
        (RequestMeta)
            new JwtAuthFilter(SECRET)
                .doFilter(new EddRequest(null, RequestMeta.newRequest()), terminal);
    assertEquals(RequestMeta.DEFAULT_REALM, meta.realm());
  }

  @Test
  void forwardsRawTokenAsAuthorizationAnnotation() throws Exception {
    String jwt = token(SECRET, "{\"sub\":\"u-1\"}");
    FilterChain terminal = req -> req.meta();
    RequestMeta meta = (RequestMeta) new JwtAuthFilter(SECRET).doFilter(withToken(jwt), terminal);
    assertEquals("Bearer " + jwt, meta.annotations().get(HttpServiceClient.AUTHORIZATION));
  }

  @Test
  void rs256VerifiesAgainstJwksAndMapsClaims() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair kp = gen.generateKeyPair();
    RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
    String jwksJson =
        "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\""
            + b64Url(pub.getModulus())
            + "\",\"e\":\""
            + b64Url(pub.getPublicExponent())
            + "\"}]}";

    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    String header =
        b64.encodeToString("{\"alg\":\"RS256\",\"kid\":\"k1\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        b64.encodeToString(
            "{\"sub\":\"rsa-user\",\"realm\":\"tenant-z\"}".getBytes(StandardCharsets.UTF_8));
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(kp.getPrivate());
    signer.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
    String jwt = header + "." + payload + "." + b64.encodeToString(signer.sign());

    FilterChain terminal = req -> req.meta();
    RequestMeta meta =
        (RequestMeta)
            new JwtAuthFilter(JwksKeys.fromJson(jwksJson)).doFilter(withToken(jwt), terminal);

    assertEquals("tenant-z", meta.realm());
    assertEquals("rsa-user", meta.user().id());
  }

  @Test
  void rs256RejectsTokenSignedByUnknownKey() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair trusted = gen.generateKeyPair();
    KeyPair attacker = gen.generateKeyPair();
    RSAPublicKey trustedPub = (RSAPublicKey) trusted.getPublic();
    String jwksJson =
        "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\""
            + b64Url(trustedPub.getModulus())
            + "\",\"e\":\""
            + b64Url(trustedPub.getPublicExponent())
            + "\"}]}";

    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    String header =
        b64.encodeToString("{\"alg\":\"RS256\",\"kid\":\"k1\"}".getBytes(StandardCharsets.UTF_8));
    String payload = b64.encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8));
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(attacker.getPrivate());
    signer.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
    String jwt = header + "." + payload + "." + b64.encodeToString(signer.sign());

    FilterChain terminal = req -> req.meta();
    JwtAuthFilter filter = new JwtAuthFilter(JwksKeys.fromJson(jwksJson));
    assertThrows(IllegalStateException.class, () -> filter.doFilter(withToken(jwt), terminal));
  }

  private static final Clock AT_2000 = Clock.fixed(Instant.ofEpochSecond(2000), ZoneOffset.UTC);

  @Test
  void rejectsExpiredToken() throws Exception {
    String jwt = token(SECRET, "{\"sub\":\"u\",\"exp\":1000}"); // expired before clock at 2000
    FilterChain terminal = req -> req.meta();
    JwtAuthFilter filter = JwtAuthFilter.builder().hs256(SECRET).clock(AT_2000).build();
    assertThrows(IllegalStateException.class, () -> filter.doFilter(withToken(jwt), terminal));
  }

  @Test
  void acceptsUnexpiredToken() throws Exception {
    String jwt = token(SECRET, "{\"sub\":\"u\",\"exp\":9999}");
    FilterChain terminal = req -> req.meta();
    RequestMeta meta =
        (RequestMeta)
            JwtAuthFilter.builder()
                .hs256(SECRET)
                .clock(AT_2000)
                .build()
                .doFilter(withToken(jwt), terminal);
    assertEquals("u", meta.user().id());
  }

  @Test
  void rejectsWrongAudience() throws Exception {
    String jwt = token(SECRET, "{\"sub\":\"u\",\"aud\":\"other-app\"}");
    FilterChain terminal = req -> req.meta();
    JwtAuthFilter filter = JwtAuthFilter.builder().hs256(SECRET).audience("my-app").build();
    assertThrows(IllegalStateException.class, () -> filter.doFilter(withToken(jwt), terminal));
  }

  @Test
  void rejectsWrongIssuer() throws Exception {
    String jwt = token(SECRET, "{\"sub\":\"u\",\"iss\":\"https://evil\"}");
    FilterChain terminal = req -> req.meta();
    JwtAuthFilter filter = JwtAuthFilter.builder().hs256(SECRET).issuer("https://good").build();
    assertThrows(IllegalStateException.class, () -> filter.doFilter(withToken(jwt), terminal));
  }

  @Test
  void acceptsMatchingAudienceAndIssuer() throws Exception {
    String jwt =
        token(SECRET, "{\"sub\":\"u\",\"aud\":\"my-app\",\"iss\":\"https://good\",\"exp\":9999}");
    FilterChain terminal = req -> req.meta();
    RequestMeta meta =
        (RequestMeta)
            JwtAuthFilter.builder()
                .hs256(SECRET)
                .audience("my-app")
                .issuer("https://good")
                .clock(AT_2000)
                .build()
                .doFilter(withToken(jwt), terminal);
    assertEquals("u", meta.user().id());
  }

  @Test
  void configurableClaimMapping() throws Exception {
    String jwt =
        token(SECRET, "{\"uid\":\"u9\",\"tenant\":\"t1\",\"my-groups\":[\"admin\",\"reader\"]}");
    FilterChain terminal = req -> req.meta();
    RequestMeta meta =
        (RequestMeta)
            JwtAuthFilter.builder()
                .hs256(SECRET)
                .realmClaim("tenant")
                .userIdClaims("uid")
                .rolesClaims("my-groups")
                .build()
                .doFilter(withToken(jwt), terminal);
    assertEquals("t1", meta.realm());
    assertEquals("u9", meta.user().id());
    assertEquals("admin", meta.user().role());
    assertTrue(meta.user().roles().contains("reader"));
  }

  @Test
  void builtFromConfig() throws Exception {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("token.iss", "https://issuer");
    props.setProperty("token.hs256-secret", new String(SECRET, StandardCharsets.UTF_8));
    props.setProperty("token.realm-claim", "tenant");
    props.setProperty("token.roles-claims", "my-groups");
    JwtAuthFilter filter =
        JwtAuthFilter.fromConfig(
            com.alphaprosoft.edd.core.config.Config.builder().fromProperties(props).build());

    String jwt =
        token(
            SECRET,
            "{\"sub\":\"u\",\"tenant\":\"t\",\"my-groups\":[\"admin\"],\"iss\":\"https://issuer\"}");
    FilterChain terminal = req -> req.meta();
    RequestMeta meta = (RequestMeta) filter.doFilter(withToken(jwt), terminal);

    assertEquals("t", meta.realm());
    assertEquals("admin", meta.user().role());
    // wrong issuer is rejected because config set token.iss
    String badIss = token(SECRET, "{\"sub\":\"u\",\"iss\":\"https://evil\"}");
    assertThrows(IllegalStateException.class, () -> filter.doFilter(withToken(badIss), terminal));
  }

  private static String b64Url(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
