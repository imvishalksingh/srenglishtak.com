package com.readiq.backend.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-controlled runtime secret only. Never accepts credentials from an API request. */
public final class ServiceAccountLoader {
  private ServiceAccountLoader() {}

  public static GoogleCredentials load(String file, String json, String scope) {
    boolean fromFile = file != null && !file.isBlank();
    boolean fromJson = json != null && !json.isBlank();
    if (fromFile == fromJson)
      throw new IllegalStateException("Configure exactly one Google service-account source: file or JSON runtime secret");
    java.io.InputStream input;
    try {
      input = fromFile ? Files.newInputStream(Path.of(file))
          : new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    } catch (Exception failure) {
      throw new IllegalStateException("Google service-account file is unavailable; check its runtime mount path and read permissions");
    }
    try (input) {
      // Restrict credential type; external-account configurations are never loaded.
      return ServiceAccountCredentials.fromStream(input).createScoped(scope);
    } catch (Exception failure) {
      // Do not attach parser causes: a secret or private file path could appear in them.
      throw new IllegalStateException("Google service-account credential data is invalid; use the original service-account JSON");
    }
  }
}
