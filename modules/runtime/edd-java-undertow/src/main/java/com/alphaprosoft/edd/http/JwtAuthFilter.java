package com.alphaprosoft.edd.http;

import com.alphaprosoft.edd.core.RequestMeta;
import com.alphaprosoft.edd.core.User;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.filter.EddRequest;
import com.alphaprosoft.edd.filter.Filter;
import com.alphaprosoft.edd.filter.FilterChain;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A {@link Filter} that reads a JWT from the request attribute {@value #TOKEN_ATTRIBUTE} (stashed
 * by the transport from the {@code Authorization: Bearer} header), verifies it, validates its
 * registered claims, maps its claims onto {@link RequestMeta}, and proceeds. The raw token is
 * stored as the {@value HttpServiceClient#AUTHORIZATION} annotation so remote deps forward the
 * caller's credentials.
 *
 * <p><b>Verification</b> is constructor/builder-selected: HS256 (shared secret), RS256/JWKS ({@link
 * JwksKeys}, key chosen by the token {@code kid}), or trust (a gateway already verified).
 *
 * <p><b>Registered-claim validation</b> (edd-core {@code lambda/jwt}): {@code exp} (and {@code
 * nbf}) are checked against an injectable {@link Clock} with optional leeway; {@code aud} and
 * {@code iss} are checked when configured. An expired/wrong-audience/wrong-issuer token is
 * rejected.
 *
 * <p><b>Claim mapping</b> is configurable via {@link Builder}: which claim yields the realm, user
 * id (ordered fallback), roles (ordered fallback), active role, and email. Defaults are
 * Cognito-style ({@code realm}, {@code sub}/{@code email}, {@code cognito:groups}/{@code roles},
 * {@code role}).
 */
public final class JwtAuthFilter implements Filter {

  public static final String TOKEN_ATTRIBUTE = "jwt";

  private final byte[] hs256Secret;
  private final JwksKeys jwks;

  private final Set<String> audiences;
  private final String issuer;
  private final Clock clock;
  private final long leewaySeconds;
  private final boolean requireExpiry;

  private final String realmClaim;
  private final List<String> userIdClaims;
  private final List<String> rolesClaims;
  private final String roleClaim;
  private final String emailClaim;

  public JwtAuthFilter() {
    this(builder());
  }

  public JwtAuthFilter(byte[] hs256Secret) {
    this(builder().hs256(hs256Secret));
  }

  public JwtAuthFilter(JwksKeys jwks) {
    this(builder().rs256(jwks));
  }

  private JwtAuthFilter(Builder b) {
    this.hs256Secret = b.hs256Secret == null ? null : b.hs256Secret.clone();
    this.jwks = b.jwks;
    this.audiences = Set.copyOf(b.audiences);
    this.issuer = b.issuer;
    this.clock = b.clock;
    this.leewaySeconds = b.leewaySeconds;
    this.requireExpiry = b.requireExpiry;
    this.realmClaim = b.realmClaim;
    this.userIdClaims = List.copyOf(b.userIdClaims);
    this.rolesClaims = List.copyOf(b.rolesClaims);
    this.roleClaim = b.roleClaim;
    this.emailClaim = b.emailClaim;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Build from the {@code token.*} configuration namespace (see CONFIG.md). */
  public static JwtAuthFilter fromConfig(Config config) {
    return builder().config(config.sub("token")).build();
  }

  @Override
  public Object doFilter(EddRequest request, FilterChain chain) {
    Object token = request.attributes().get(TOKEN_ATTRIBUTE);
    if (token instanceof String jwt && !jwt.isBlank()) {
      request.meta(applyClaims(request.meta(), jwt));
    }
    return chain.proceed(request);
  }

  private JsonNode parse(String jwt) {
    String[] parts = jwt.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Malformed JWT");
    }
    verifySignature(parts[0], parts[1], parts[2]);
    JsonNode claims;
    try {
      claims = Wire.MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
    } catch (Exception e) {
      throw new IllegalArgumentException("Unparseable JWT payload", e);
    }
    validateRegisteredClaims(claims);
    return claims;
  }

  private void verifySignature(String header, String payload, String signature) {
    if (hs256Secret != null) {
      verifyHs256(header, payload, signature);
    } else if (jwks != null) {
      verifyRs256(header, payload, signature);
    }
  }

  private void verifyHs256(String header, String payload, String signature) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(hs256Secret, "HmacSHA256"));
      byte[] expected = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
      String expectedB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(expected);
      if (!expectedB64.equals(signature)) {
        throw new IllegalStateException("JWT signature verification failed");
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("JWT signature verification error", e);
    }
  }

  private void verifyRs256(String header, String payload, String signature) {
    try {
      JsonNode head = Wire.MAPPER.readTree(Base64.getUrlDecoder().decode(header));
      String kid = head.path("kid").asText();
      PublicKey key = jwks.keyFor(kid);
      if (key == null) {
        throw new IllegalStateException("No JWKS key for kid '" + kid + "'");
      }
      Signature rsa = Signature.getInstance("SHA256withRSA");
      rsa.initVerify(key);
      rsa.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
      if (!rsa.verify(Base64.getUrlDecoder().decode(signature))) {
        throw new IllegalStateException("JWT signature verification failed");
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("JWT signature verification error", e);
    }
  }

  private void validateRegisteredClaims(JsonNode claims) {
    long now = clock.instant().getEpochSecond();
    if (claims.hasNonNull("exp")) {
      if (now > claims.get("exp").asLong() + leewaySeconds) {
        throw new IllegalStateException("JWT expired");
      }
    } else if (requireExpiry) {
      throw new IllegalStateException("JWT missing required exp claim");
    }
    if (claims.hasNonNull("nbf") && now + leewaySeconds < claims.get("nbf").asLong()) {
      throw new IllegalStateException("JWT not yet valid (nbf)");
    }
    if (!audiences.isEmpty() && !audienceMatches(claims.get("aud"))) {
      throw new IllegalStateException("JWT audience not accepted");
    }
    if (issuer != null && !issuer.equals(text(claims, "iss"))) {
      throw new IllegalStateException("JWT issuer not accepted");
    }
  }

  private boolean audienceMatches(JsonNode aud) {
    if (aud == null || aud.isNull()) {
      return false;
    }
    if (aud.isArray()) {
      for (JsonNode a : aud) {
        if (audiences.contains(a.asText())) {
          return true;
        }
      }
      return false;
    }
    return audiences.contains(aud.asText());
  }

  private RequestMeta applyClaims(RequestMeta meta, String jwt) {
    JsonNode claims = parse(jwt);
    RequestMeta.Builder b = RequestMeta.builder(meta);
    b.annotation(HttpServiceClient.AUTHORIZATION, "Bearer " + jwt);
    if (claims.hasNonNull(realmClaim)) {
      b.realm(claims.get(realmClaim).asText());
    }
    List<String> roles = roles(claims);
    String role =
        claims.hasNonNull(roleClaim)
            ? claims.get(roleClaim).asText()
            : (roles.isEmpty() ? "anonymous" : roles.getFirst());
    b.user(
        new User(
            firstClaim(claims, userIdClaims), role, roles, text(claims, emailClaim), Map.of()));
    return b.build();
  }

  private String firstClaim(JsonNode claims, List<String> names) {
    for (String name : names) {
      if (claims.hasNonNull(name)) {
        return claims.get(name).asText();
      }
    }
    return null;
  }

  private static String text(JsonNode claims, String name) {
    return claims.hasNonNull(name) ? claims.get(name).asText() : null;
  }

  private List<String> roles(JsonNode claims) {
    List<String> roles = new ArrayList<>();
    for (String name : rolesClaims) {
      JsonNode groups = claims.get(name);
      if (groups == null) {
        continue;
      }
      if (groups.isArray()) {
        groups.forEach(n -> roles.add(n.asText()));
      } else if (groups.isTextual()) {
        for (String r : groups.asText().split(",")) {
          if (!r.isBlank()) {
            roles.add(r.trim());
          }
        }
      }
      if (!roles.isEmpty()) {
        break;
      }
    }
    return roles;
  }

  public static final class Builder {
    private byte[] hs256Secret;
    private JwksKeys jwks;
    private final Set<String> audiences = new LinkedHashSet<>();
    private String issuer;
    private Clock clock = Clock.systemUTC();
    private long leewaySeconds = 0;
    private boolean requireExpiry = false;

    private String realmClaim = "realm";
    private List<String> userIdClaims = List.of("sub", "email");
    private List<String> rolesClaims = List.of("cognito:groups", "roles");
    private String roleClaim = "role";
    private String emailClaim = "email";

    private Builder() {}

    public Builder hs256(byte[] secret) {
      this.hs256Secret = secret.clone();
      this.jwks = null;
      return this;
    }

    public Builder rs256(JwksKeys jwks) {
      this.jwks = jwks;
      this.hs256Secret = null;
      return this;
    }

    /** Trust the claims without verifying the signature (a gateway already verified the token). */
    public Builder trust() {
      this.hs256Secret = null;
      this.jwks = null;
      return this;
    }

    public Builder audience(String... audiences) {
      for (String a : audiences) {
        this.audiences.add(a);
      }
      return this;
    }

    public Builder issuer(String issuer) {
      this.issuer = issuer;
      return this;
    }

    public Builder clock(Clock clock) {
      this.clock = clock;
      return this;
    }

    public Builder leewaySeconds(long leewaySeconds) {
      this.leewaySeconds = leewaySeconds;
      return this;
    }

    public Builder requireExpiry(boolean requireExpiry) {
      this.requireExpiry = requireExpiry;
      return this;
    }

    public Builder realmClaim(String realmClaim) {
      this.realmClaim = realmClaim;
      return this;
    }

    public Builder userIdClaims(String... claims) {
      this.userIdClaims = List.of(claims);
      return this;
    }

    public Builder rolesClaims(String... claims) {
      this.rolesClaims = List.of(claims);
      return this;
    }

    public Builder roleClaim(String roleClaim) {
      this.roleClaim = roleClaim;
      return this;
    }

    public Builder emailClaim(String emailClaim) {
      this.emailClaim = emailClaim;
      return this;
    }

    /**
     * Apply settings from the {@code token} config namespace ({@code iss}, {@code aud}, claim
     * names, …).
     */
    public Builder config(Config token) {
      token.get("iss").ifPresent(this::issuer);
      if (!token.getList("aud").isEmpty()) {
        audience(token.getList("aud").toArray(String[]::new));
      }
      token.get("realm-claim").ifPresent(this::realmClaim);
      if (!token.getList("user-id-claims").isEmpty()) {
        userIdClaims(token.getList("user-id-claims").toArray(String[]::new));
      }
      if (!token.getList("roles-claims").isEmpty()) {
        rolesClaims(token.getList("roles-claims").toArray(String[]::new));
      }
      token.get("role-claim").ifPresent(this::roleClaim);
      token.get("email-claim").ifPresent(this::emailClaim);
      leewaySeconds(token.getLong("leeway-seconds", leewaySeconds));
      requireExpiry(token.getBoolean("require-expiry", requireExpiry));
      token.get("hs256-secret").ifPresent(s -> hs256(s.getBytes(StandardCharsets.UTF_8)));
      token.get("jwks").ifPresent(j -> rs256(JwksKeys.fromJson(j)));
      return this;
    }

    public JwtAuthFilter build() {
      return new JwtAuthFilter(this);
    }
  }
}
