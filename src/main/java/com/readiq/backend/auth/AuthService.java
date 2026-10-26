package com.readiq.backend.auth;

import static com.readiq.backend.api.Input.*;

import com.readiq.backend.api.*;
import com.readiq.backend.data.Database;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final SupabaseClient supabase;
  private final Database db;
  private final AccessTokenVerifier verifier;
  private final boolean allowAdmin;
  private final String adminSecret, resetRedirect;

  public AuthService(
      SupabaseClient supabase,
      Database db,
      AccessTokenVerifier verifier,
      @Value("${security.allow-admin-registration:false}") boolean allowAdmin,
      @Value("${security.admin-registration-secret:}") String adminSecret,
      @Value("${security.password-reset-redirect:}") String resetRedirect) {
    this.supabase = supabase;
    this.db = db;
    this.verifier = verifier;
    this.allowAdmin = allowAdmin;
    this.adminSecret = adminSecret;
    this.resetRedirect = resetRedirect;
  }

  public Map<String, Object> login(Map<String, Object> b) {
    credentials(b);
    return session(
        supabase.auth(
            "POST",
            "/token?grant_type=password",
            map("email", b.get("email"), "password", b.get("password")),
            null));
  }

  public Map<String, Object> google(Map<String, Object> b) {
    var payload = map("provider", "google", "id_token", text(b, "id_token", 20_000, true));
    if (b.get("nonce") != null) payload.put("nonce", text(b, "nonce", 512, true));
    return session(supabase.auth("POST", "/token?grant_type=id_token", payload, null));
  }

  public Map<String, Object> refresh(Map<String, Object> b) {
    return session(
        supabase.auth(
            "POST",
            "/token?grant_type=refresh_token",
            map("refresh_token", text(b, "refresh_token", 4096, true)),
            null));
  }

  public Map<String, Object> register(Map<String, Object> b) {
    credentials(b);
    String name = text(b, "full_name", 100, true), supplied = text(b, "admin_secret", 1024, false);
    boolean admin =
        supplied != null
            && allowAdmin
            && !adminSecret.isBlank()
            && MessageDigest.isEqual(
                adminSecret.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    if (supplied != null && !admin)
      throw new ApiException(403, "Admin registration is disabled or not authorized");
    var raw =
        supabase.auth(
            "POST",
            "/signup",
            map(
                "email",
                b.get("email"),
                "password",
                b.get("password"),
                "data",
                map("full_name", name)),
            null);
    Map<String, Object> profile = ensureProfile(user(raw));
    if (admin) {
      db.execute("update profiles set role='admin' where id=?", uuid(profile.get("id")));
      profile = db.byId("profiles", uuid(profile.get("id")));
    }
    return map(
        "message",
        "User registered successfully",
        "email_confirmed",
        raw.get("access_token") != null,
        "user",
        profile,
        "session",
        tokens(raw));
  }

  public Map<String, Object> forgot(Map<String, Object> b) {
    String email = email(b), path = "/recover";
    if (!resetRedirect.isBlank())
      path += "?redirect_to=" + java.net.URLEncoder.encode(resetRedirect, StandardCharsets.UTF_8);
    try {
      supabase.auth("POST", path, map("email", email), null);
    } catch (ApiException ignored) {
    }
    return map("message", "If this account exists, a password reset email has been sent.");
  }

  public Map<String, Object> logout(String token) {
    supabase.auth("POST", "/logout", null, token);
    verifier.evict(token);
    java.time.Instant until = java.time.Instant.now().plusSeconds(86400);
    try {
      var expiry = com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getExpirationTime();
      if (expiry != null) until = expiry.toInstant().plusSeconds(60);
    } catch (Exception ignored) {
    }
    db.execute(
        "insert into revoked_access_tokens(token_hash,expires_at) values(?,?) on conflict do"
            + " nothing",
        AccessTokenVerifier.hash(token),
        java.time.OffsetDateTime.ofInstant(until, java.time.ZoneOffset.UTC));
    return map("message", "Logged out successfully");
  }

  public Map<String, Object> authenticate(String token) {
    if (db.count(
            "select count(*) from revoked_access_tokens where token_hash=? and expires_at>now()",
            AccessTokenVerifier.hash(token))
        > 0) throw new ApiException(401, "Session has been signed out");
    return ensureProfile(user(verifier.verify(token)));
  }

  private Map<String, Object> session(Map<String, Object> raw) {
    var tokens = tokens(raw);
    if (tokens == null) throw new ApiException(401, "No authentication session returned");
    return map("user", ensureProfile(user(raw)), "session", tokens);
  }

  private Map<String, Object> tokens(Map<String, Object> raw) {
    if (raw.get("access_token") == null) return null;
    long expiresAt =
        raw.get("expires_at") instanceof Number n
            ? n.longValue()
            : Instant.now().getEpochSecond()
                + ((Number) raw.getOrDefault("expires_in", 3600)).longValue();
    return map(
        "access_token",
        raw.get("access_token"),
        "refresh_token",
        raw.get("refresh_token"),
        "expires_at",
        expiresAt,
        "expires_in",
        raw.getOrDefault("expires_in", 3600),
        "token_type",
        "bearer");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> user(Map<String, Object> raw) {
    var result = raw.get("user") instanceof Map<?, ?> m ? (Map<String, Object>) m : raw;
    if (result.get("id") == null) throw new ApiException(401, "No authenticated user returned");
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> ensureProfile(Map<String, Object> user) {
    UUID id = uuid(user.get("id"));
    var existing = db.optional("select * from profiles where id=?", id);
    if (existing.isPresent()) return existing.get();
    var meta =
        user.get("user_metadata") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : Map.<String, Object>of();
    // Never overwrite roles from user metadata or a caller-supplied token role.
    db.execute(
        "insert into profiles(id,email,full_name,avatar_url,role) values(?,?,?,?,'user') on"
            + " conflict(id) do nothing",
        id,
        user.get("email"),
        meta.getOrDefault("full_name", meta.get("name")),
        meta.getOrDefault("avatar_url", meta.get("picture")));
    return db.byId("profiles", id);
  }

  private void credentials(Map<String, Object> b) {
    email(b);
    if (text(b, "password", 128, true).length() < 6)
      throw ApiException.bad("Password must contain at least 6 characters");
  }

  private String email(Map<String, Object> b) {
    String email = text(b, "email", 254, true);
    if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw ApiException.bad("Invalid email");
    return email;
  }
}
