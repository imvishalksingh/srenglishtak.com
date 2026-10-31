package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import com.readiq.backend.auth.ServiceAccountLoader;
import com.readiq.backend.payment.PaymentSettings;
import com.readiq.backend.reader.ReaderSettings;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ServiceAccountLoaderTest {
  @TempDir Path temp;

  static String fixture() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
    String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
        .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
        + "\n-----END PRIVATE KEY-----\n";
    return new ObjectMapper().writeValueAsString(java.util.Map.of("type", "service_account",
        "client_email", "fixture@example.test", "private_key", pem, "private_key_id", "fixture",
        "client_id", "123", "token_uri", "https://oauth2.googleapis.com/token"));
  }

  @Test void supportsJsonRuntimeSecretWithoutNetwork() throws Exception {
    var credentials = ServiceAccountLoader.load("", fixture(), "scope-fixture");
    assertInstanceOf(ServiceAccountCredentials.class, credentials);
    assertEquals("fixture@example.test", ((ServiceAccountCredentials) credentials).getClientEmail());
  }

  @Test void existingMountedFileRemainsSupported() throws Exception {
    var file = temp.resolve("fixture.json"); Files.writeString(file, fixture());
    assertInstanceOf(ServiceAccountCredentials.class,
        ServiceAccountLoader.load(file.toString(), "", "scope-fixture"));
  }

  @Test void rejectsMissingConflictingMalformedAndExternalCredentialsWithoutLeaking() {
    for (var source : java.util.List.of(new String[]{"", ""}, new String[]{"private/path", "secret-json"},
        new String[]{"", "secret-json"}, new String[]{"", "{\"type\":\"external_account\"}"})) {
      var failure = assertThrows(IllegalStateException.class,
          () -> ServiceAccountLoader.load(source[0], source[1], "scope-fixture"));
      assertTrue(failure.getMessage().contains("Google service-account"));
      assertFalse(failure.getMessage().contains("secret-json"));
      assertFalse(failure.getMessage().contains("private/path"));
      assertNull(failure.getCause());
    }
  }

  @Test void jsonEnablesPaymentsAndReaderWithoutExposingSecretInSettings() {
    var payments = new PaymentSettings(); payments.setMode("live");
    payments.setGoogleCredentialsJson("private-json"); payments.setTokenEncryptionKey("existing-key");
    assertTrue(payments.googleEnabled());
    payments.setMode("disabled"); assertFalse(payments.googleEnabled());
    var reader = new ReaderSettings(123, "", "private-json", "com.fixture.app", "ab".repeat(32), 10);
    assertTrue(reader.hasCredentials()); assertFalse(reader.toString().contains("private-json"));
  }
}
