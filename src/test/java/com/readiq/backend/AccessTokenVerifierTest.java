package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.auth.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AccessTokenVerifierTest {
  @Test
  void asymmetricTokensValidateSignatureIssuerAudienceAndExpiryWithoutUserHttpCall()
      throws Exception {
    RSAKey key = new RSAKeyGenerator(2048).keyID("fixture").generate();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] jwks =
        new JWKSet(key.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    server.createContext(
        "/auth/v1/.well-known/jwks.json",
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, jwks.length);
          exchange.getResponseBody().write(jwks);
          exchange.close();
        });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      var remote = mock(SupabaseClient.class);
      var verifier = new AccessTokenVerifier(remote, base, 30, true);
      UUID id = UUID.randomUUID();
      assertEquals(
          id.toString(),
          verifier
              .verify(
                  token(
                      key, base + "/auth/v1", "authenticated", id, Instant.now().plusSeconds(300)))
              .get("id"));
      assertThrows(
          ApiException.class,
          () ->
              verifier.verify(
                  token(
                      key,
                      "https://wrong.example/auth/v1",
                      "authenticated",
                      id,
                      Instant.now().plusSeconds(300))));
      assertThrows(
          ApiException.class,
          () ->
              verifier.verify(
                  token(key, base + "/auth/v1", "other-app", id, Instant.now().plusSeconds(300))));
      assertThrows(
          ApiException.class,
          () ->
              verifier.verify(
                  token(
                      key,
                      base + "/auth/v1",
                      "authenticated",
                      id,
                      Instant.now().minusSeconds(120))));
      RSAKey attacker = new RSAKeyGenerator(2048).keyID("fixture").generate();
      assertThrows(
          ApiException.class,
          () ->
              verifier.verify(
                  token(
                      attacker,
                      base + "/auth/v1",
                      "authenticated",
                      id,
                      Instant.now().plusSeconds(300))));
      verifyNoInteractions(remote);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void onlySuccessfullyVerifiedRemoteIdentitiesAreCachedAndLogoutCanEvict() {
    var remote = mock(SupabaseClient.class);
    when(remote.auth("GET", "/user", null, "legacy-token"))
        .thenReturn(Map.of("id", UUID.randomUUID().toString()));
    var verifier = new AccessTokenVerifier(remote, "https://example.supabase.co", 30, false);
    verifier.verify("legacy-token");
    verifier.verify("legacy-token");
    verify(remote, times(1)).auth("GET", "/user", null, "legacy-token");
    verifier.evict("legacy-token");
    verifier.verify("legacy-token");
    verify(remote, times(2)).auth("GET", "/user", null, "legacy-token");
  }

  private String token(RSAKey key, String issuer, String audience, UUID id, Instant expires)
      throws Exception {
    SignedJWT token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture").build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(id.toString())
                .expirationTime(Date.from(expires))
                .issueTime(new Date())
                .build());
    token.sign(new RSASSASigner(key));
    return token.serialize();
  }
}
