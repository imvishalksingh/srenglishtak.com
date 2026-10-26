package com.readiq.backend;

import static com.readiq.backend.api.Input.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.readiq.backend.api.*;
import com.readiq.backend.auth.*;
import com.readiq.backend.data.Database;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
  @Test
  void sixCharacterExistingPasswordsCanLoginAndExpiryIsDerived() {
    var remote = mock(SupabaseClient.class);
    var db = mock(Database.class);
    UUID id = UUID.randomUUID();
    when(remote.auth(eq("POST"), eq("/token?grant_type=password"), anyMap(), isNull()))
        .thenReturn(
            map(
                "access_token",
                "token",
                "refresh_token",
                "refresh",
                "expires_in",
                3600,
                "user",
                map("id", id.toString(), "email", "reader@example.test")));
    when(db.byId("profiles", id)).thenReturn(map("id", id.toString(), "role", "user"));
    var auth = new AuthService(remote, db, mock(AccessTokenVerifier.class), false, "", "");
    var response = auth.login(map("email", "reader@example.test", "password", "123456"));
    assertNotNull(response.get("session"));
  }

  @Test
  void publicRegistrationCannotGrantAdminWithoutExplicitProvisioning() {
    var remote = mock(SupabaseClient.class);
    var auth =
        new AuthService(
            remote, mock(Database.class), mock(AccessTokenVerifier.class), false, "secret", "");
    var ex =
        assertThrows(
            ApiException.class,
            () ->
                auth.register(
                    map(
                        "email",
                        "r@example.test",
                        "password",
                        "123456",
                        "full_name",
                        "Reader",
                        "admin_secret",
                        "secret")));
    assertEquals(403, ex.status());
    verifyNoInteractions(remote);
  }

  @Test
  void typedValidationRejectsFractionalIntegersAndStringBooleans() {
    assertThrows(ApiException.class, () -> integer(1.1, "page", 0, 100));
    assertThrows(ApiException.class, () -> bool("false", "is_excited"));
  }

  @Test
  void signupReturnsTopLevelSessionAndUser() {
    var remote = mock(SupabaseClient.class);
    var db = mock(Database.class);
    UUID id = UUID.randomUUID();
    when(remote.auth(eq("POST"), eq("/signup"), anyMap(), isNull()))
        .thenReturn(
            map(
                "access_token",
                "token",
                "refresh_token",
                "refresh",
                "expires_in",
                3600,
                "user",
                map("id", id.toString(), "email", "reader@example.test")));
    when(db.byId("profiles", id)).thenReturn(map("id", id.toString(), "role", "user"));
    var result =
        new AuthService(remote, db, mock(AccessTokenVerifier.class), false, "", "")
            .register(
                map("email", "reader@example.test", "password", "123456", "full_name", "Reader"));
    assertEquals(true, result.get("email_confirmed"));
    assertNotNull(result.get("user"));
    assertNotNull(result.get("session"));
  }

  @Test
  void refreshUsesRefreshGrantAndKeepsProfileRole() {
    var remote = mock(SupabaseClient.class);
    var db = mock(Database.class);
    UUID id = UUID.randomUUID();
    when(remote.auth(eq("POST"), eq("/token?grant_type=refresh_token"), anyMap(), isNull()))
        .thenReturn(
            map(
                "access_token",
                "new-token",
                "refresh_token",
                "new-refresh",
                "expires_in",
                3600,
                "user",
                map("id", id.toString())));
    when(db.byId("profiles", id)).thenReturn(map("id", id.toString(), "role", "admin"));
    var result =
        new AuthService(remote, db, mock(AccessTokenVerifier.class), false, "", "")
            .refresh(map("refresh_token", "old-refresh"));
    assertEquals("admin", ((Map<?, ?>) result.get("user")).get("role"));
  }
}
