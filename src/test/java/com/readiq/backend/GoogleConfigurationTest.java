package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import com.readiq.backend.payment.*;
import com.readiq.backend.reader.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleConfigurationTest {
  private PaymentSettings settings() {
    var settings = new PaymentSettings(); settings.setMode("live");
    settings.setGoogleCredentialsFile("missing-fixture.json");
    settings.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
    return settings;
  }

  @Test void invalidEncryptionKeyHasItsOwnSanitizedStartupMessage() {
    for (String key : java.util.List.of("private-invalid-key", Base64.getEncoder().encodeToString(new byte[16]))) {
      var settings = settings(); settings.setTokenEncryptionKey(key);
      var failure = assertThrows(IllegalStateException.class,
          () -> new GooglePlayClient(settings, new ObjectMapper()).validateConfiguration());
      assertTrue(failure.getMessage().startsWith("PAYMENT_TOKEN_ENCRYPTION_KEY must be base64 of 32 bytes"));
      assertFalse(failure.getMessage().contains(key)); assertNull(failure.getCause());
    }
  }

  @Test void missingMountedFileIsDistinguishedFromEncryptionFailure() {
    var failure = assertThrows(IllegalStateException.class,
        () -> new GooglePlayClient(settings(), new ObjectMapper()).validateConfiguration());
    assertTrue(failure.getMessage().contains("mount path and read permissions"));
    assertFalse(failure.getMessage().contains("missing-fixture"));
    assertNull(failure.getCause());
  }

  @Test void malformedAndConflictingCredentialSourcesAreSanitized() {
    var settings = settings(); settings.setGoogleCredentialsFile("");
    settings.setGoogleCredentialsJson("private-invalid-json");
    var failure = assertThrows(IllegalStateException.class,
        () -> new GooglePlayClient(settings, new ObjectMapper()).validateConfiguration());
    assertTrue(failure.getMessage().contains("credential data is invalid"));
    assertFalse(failure.getMessage().contains("private-invalid-json"));
    settings.setGoogleCredentialsFile("private-path.json");
    var conflicting = assertThrows(IllegalStateException.class,
        () -> new GooglePlayClient(settings, new ObjectMapper()).validateConfiguration());
    assertTrue(conflicting.getMessage().contains("exactly one"));
    assertFalse(conflicting.getMessage().contains("private-path"));
  }

  @Test void bothGoogleClientsAcceptJsonConfigurationWithoutContactingGoogle() throws Exception {
    String json = ServiceAccountLoaderTest.fixture();
    var settings = settings(); settings.setGoogleCredentialsFile(""); settings.setGoogleCredentialsJson(json);
    assertDoesNotThrow(() -> new GooglePlayClient(settings, new ObjectMapper()).validateConfiguration());
    var reader = new ReaderSettings(123, "", json, "com.fixture.app", "ab".repeat(32), 10);
    assertDoesNotThrow(() -> new PlayIntegrityClient(reader, new ObjectMapper()).validateConfiguration());
  }
}
