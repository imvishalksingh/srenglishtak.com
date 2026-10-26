package com.readiq.backend.auth;

import com.nimbusds.jwt.SignedJWT;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.api.Input;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

/**
 * Only identities are cached. Database roles are read on every request. No unverified JWT claims
 * authorize a request.
 */
@Component
public class AccessTokenVerifier {
  private record Verified(Map<String, Object> user, Instant expires) {}

  private final Map<String, Verified> cache = new LinkedHashMap<>();
  private final SupabaseClient remote;
  private final NimbusJwtDecoder decoder;
  private final int ttl;
  private final boolean local;

  public AccessTokenVerifier(
      SupabaseClient remote,
      @Value("${supabase.url}") String url,
      @Value("${security.auth-cache-seconds:30}") int ttl,
      @Value("${security.local-jwt-verification:true}") boolean local) {
    this.remote = remote;
    this.ttl = Math.clamp(ttl, 0, 60);
    this.local = local;
    String issuer = url.replaceAll("/+$", "") + "/auth/v1";
    var transport = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    transport.setConnectTimeout(5000);
    transport.setReadTimeout(5000);
    this.decoder =
        NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
            .restOperations(new org.springframework.web.client.RestTemplate(transport))
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            jwt ->
                jwt.getExpiresAt() != null
                        && jwt.getAudience().contains("authenticated")
                        && jwt.getSubject() != null
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
  }

  public Map<String, Object> verify(String token) {
    String algorithm = "";
    try {
      algorithm = SignedJWT.parse(token).getHeader().getAlgorithm().getName();
    } catch (Exception ignored) {
      /* The Auth server rejects opaque or malformed legacy credentials. */
    }
    if (local && Set.of("ES256", "RS256").contains(algorithm)) {
      try {
        Jwt jwt = decoder.decode(token);
        Input.uuid(jwt.getSubject());
        return Input.map(
            "id",
            jwt.getSubject(),
            "email",
            jwt.getClaimAsString("email"),
            "user_metadata",
            jwt.getClaim("user_metadata"));
      } catch (JwtException ex) {
        throw new ApiException(401, "Invalid access token");
      }
    }
    String hash = hash(token);
    synchronized (cache) {
      Verified existing = cache.get(hash);
      if (existing != null && existing.expires().isAfter(Instant.now())) return existing.user();
      cache.remove(hash);
    }
    Map<String, Object> user = remote.auth("GET", "/user", null, token);
    Instant expires = Instant.now().plusSeconds(ttl);
    try {
      expires =
          expires.isBefore(SignedJWT.parse(token).getJWTClaimsSet().getExpirationTime().toInstant())
              ? expires
              : SignedJWT.parse(token).getJWTClaimsSet().getExpirationTime().toInstant();
    } catch (Exception ignored) {
      /* Cache never exceeds the small configured TTL. */
    }
    synchronized (cache) {
      if (cache.size() >= 10_000)
        cache.entrySet().removeIf(e -> !e.getValue().expires().isAfter(Instant.now()));
      if (cache.size() < 10_000 && ttl > 0)
        cache.put(
            hash, new Verified(Collections.unmodifiableMap(new LinkedHashMap<>(user)), expires));
    }
    return user;
  }

  public void evict(String token) {
    synchronized (cache) {
      cache.remove(hash(token));
    }
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
