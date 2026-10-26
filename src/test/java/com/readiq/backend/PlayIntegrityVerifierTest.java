package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.reader.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PlayIntegrityVerifierTest {
  private final PlayIntegrityClient google = mock(PlayIntegrityClient.class);
  private final ReaderSettings settings = new ReaderSettings(123, "fixture.json", "com.fixture.app", "ab".repeat(32), 10);
  private final PlayIntegrityVerifier verifier = new PlayIntegrityVerifier(settings, google);

  private ObjectNode valid() {
    return JsonMapper.builder().build().valueToTree(Map.of(
        "requestDetails", Map.of("requestPackageName", "com.fixture.app", "requestHash", "bound-hash", "timestampMillis", Instant.now().toEpochMilli()),
        "appIntegrity", Map.of("appRecognitionVerdict", "PLAY_RECOGNIZED", "packageName", "com.fixture.app", "versionCode", "10",
            "certificateSha256Digest", List.of(Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex("ab".repeat(32))))),
        "accountDetails", Map.of("appLicensingVerdict", "LICENSED"),
        "deviceIntegrity", Map.of("deviceRecognitionVerdict", List.of("MEETS_DEVICE_INTEGRITY")),
        "environmentDetails", Map.of("appAccessRiskVerdict", Map.of("appsDetected", List.of("KNOWN_INSTALLED")))));
  }

  @Test void acceptsOnlyTheGoogleVerifiedBoundVerdict() {
    when(google.decode("opaque-token")).thenReturn(valid());
    assertDoesNotThrow(() -> verifier.verify("opaque-token", "bound-hash"));
    assertThrows(ApiException.class, () -> verifier.verify("opaque-token", "different-request"));
  }

  @Test void rejectsRepackedOldUnlicensedRootedOrCapturingApps() {
    for (String field : List.of("signature", "old", "repacked", "license", "device", "capture", "risk-missing", "future", "expired", "package")) {
      var body = valid();
      switch (field) {
        case "signature" -> ((ObjectNode)body.path("appIntegrity")).putArray("certificateSha256Digest").add("wrong");
        case "old" -> ((ObjectNode)body.path("appIntegrity")).put("versionCode", "9");
        case "repacked" -> ((ObjectNode)body.path("appIntegrity")).put("appRecognitionVerdict", "UNRECOGNIZED_VERSION");
        case "license" -> ((ObjectNode)body.path("accountDetails")).put("appLicensingVerdict", "UNLICENSED");
        case "device" -> ((ObjectNode)body.path("deviceIntegrity")).putArray("deviceRecognitionVerdict").add("MEETS_BASIC_INTEGRITY");
        case "capture" -> ((ObjectNode)body.path("environmentDetails").path("appAccessRiskVerdict")).putArray("appsDetected").add("UNKNOWN_CAPTURING");
        case "risk-missing" -> body.remove("environmentDetails");
        case "future" -> ((ObjectNode)body.path("requestDetails")).put("timestampMillis", Instant.now().plusSeconds(60).toEpochMilli());
        case "expired" -> ((ObjectNode)body.path("requestDetails")).put("timestampMillis", Instant.now().minusSeconds(100).toEpochMilli());
        case "package" -> ((ObjectNode)body.path("appIntegrity")).put("packageName", "com.other.app");
      }
      when(google.decode("opaque-token")).thenReturn(body);
      assertThrows(ApiException.class, () -> verifier.verify("opaque-token", "bound-hash"), field);
    }
  }

  @Test void missingServerSetupNeverFallsBackToTrustingTheClient() {
    var missing = new PlayIntegrityVerifier(new ReaderSettings(0, "", "com.fixture.app", "", 10), google);
    assertThrows(ApiException.class, () -> missing.verify("token", "hash"));
    verifyNoInteractions(google);
  }
}
