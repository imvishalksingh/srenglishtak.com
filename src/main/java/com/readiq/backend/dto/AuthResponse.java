package com.readiq.backend.dto;

import static com.readiq.backend.api.Input.uuid;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record AuthResponse(Profile user, Session session) {
  public record Profile(
      UUID id,
      String email,
      String full_name,
      String avatar_url,
      String role,
      @JsonProperty("class") String studentClass,
      String learning_goal,
      String created_at) {
    public static Profile from(Map<String, Object> r) {
      return new Profile(
          uuid(r.get("id")),
          s(r, "email"),
          s(r, "full_name"),
          s(r, "avatar_url"),
          s(r, "role"),
          s(r, "class"),
          s(r, "learning_goal"),
          s(r, "created_at"));
    }
  }

  public record Session(
      String access_token,
      String refresh_token,
      long expires_at,
      long expires_in,
      String token_type) {
    public static Session from(Map<String, Object> r) {
      return r == null
          ? null
          : new Session(
              s(r, "access_token"),
              s(r, "refresh_token"),
              ((Number) r.get("expires_at")).longValue(),
              ((Number) r.get("expires_in")).longValue(),
              s(r, "token_type"));
    }
  }

  public record Registration(
      String message, boolean email_confirmed, Profile user, Session session) {}

  @SuppressWarnings("unchecked")
  public static AuthResponse from(Map<String, Object> r) {
    return new AuthResponse(
        Profile.from((Map<String, Object>) r.get("user")),
        Session.from((Map<String, Object>) r.get("session")));
  }

  public static Registration registration(Map<String, Object> r) {
    var response = from(r);
    return new Registration(
        s(r, "message"),
        Boolean.TRUE.equals(r.get("email_confirmed")),
        response.user(),
        response.session());
  }

  private static String s(Map<String, Object> r, String key) {
    return Objects.toString(r.get(key), null);
  }
}
