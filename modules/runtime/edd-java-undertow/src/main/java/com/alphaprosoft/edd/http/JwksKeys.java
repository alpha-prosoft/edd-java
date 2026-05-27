package com.alphaprosoft.edd.http;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Supplies the verification {@link PublicKey} for a JWT {@code kid} (key id) — the RS256/JWKS
 * counterpart to a shared HS256 secret. {@link #fromJson(String)} parses a standard JWKS document
 * ({@code {"keys":[{kty,kid,n,e}]}}) into an immutable lookup; production code would refresh it
 * from a Cognito/OIDC {@code /.well-known/jwks.json} endpoint.
 */
@FunctionalInterface
public interface JwksKeys {

  PublicKey keyFor(String kid);

  static JwksKeys fromJson(String jwksJson) {
    try {
      Map<String, PublicKey> keys = new LinkedHashMap<>();
      JsonNode root = Wire.MAPPER.readTree(jwksJson);
      for (JsonNode jwk : root.path("keys")) {
        if (!"RSA".equals(jwk.path("kty").asText())) {
          continue;
        }
        BigInteger modulus =
            new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("n").asText()));
        BigInteger exponent =
            new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("e").asText()));
        PublicKey key =
            java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));
        keys.put(jwk.path("kid").asText(), key);
      }
      Map<String, PublicKey> immutable = Map.copyOf(keys);
      return immutable::get;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JWKS document", e);
    }
  }
}
