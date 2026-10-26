package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PlayIntegrityVerifier {
  private final ReaderSettings settings;
  private final PlayIntegrityClient google;

  public PlayIntegrityVerifier(ReaderSettings settings, PlayIntegrityClient google) {
    this.settings = settings;
    this.google = google;
  }

  public void requireConfigured() {
    if (settings.cloudProjectNumber() <= 0 || settings.credentialsFile().isBlank()
        || settings.certificateSha256().isBlank())
      throw new ApiException(503, "Protected reader setup is incomplete");
  }

  public void verify(String token, String expectedHash) {
    requireConfigured();
    var payload = google.decode(token);
    var details = payload.path("requestDetails");
    long now = Instant.now().toEpochMilli(), time = details.path("timestampMillis").asLong(0);
    if (!settings.packageName().equals(details.path("requestPackageName").asText())
        || !expectedHash.equals(details.path("requestHash").asText())
        || time > now + 10000 || time < now - 90000) deny();
    var app = payload.path("appIntegrity");
    if (!"PLAY_RECOGNIZED".equals(app.path("appRecognitionVerdict").asText())
        || !settings.packageName().equals(app.path("packageName").asText())
        || app.path("versionCode").asLong(0) < Math.max(10, settings.minimumVersionCode())) deny();
    var accepted = Arrays.stream(settings.certificateSha256().split(","))
        .map(s -> s.replace(":", "").trim().toLowerCase(Locale.ROOT)).toList();
    boolean signer = false;
    for (var cert : app.path("certificateSha256Digest")) {
      try {
        String hex = HexFormat.of().formatHex(Base64.getUrlDecoder().decode(cert.asText()));
        if (accepted.contains(hex)) signer = true;
      } catch (IllegalArgumentException ignored) { /* A malformed verdict fails closed. */ }
    }
    if (!signer || !"LICENSED".equals(payload.path("accountDetails").path("appLicensingVerdict").asText())) deny();
    boolean device = false;
    for (var label : payload.path("deviceIntegrity").path("deviceRecognitionVerdict"))
      if ("MEETS_DEVICE_INTEGRITY".equals(label.asText())) device = true;
    if (!device) deny();
    var risks = payload.path("environmentDetails").path("appAccessRiskVerdict").path("appsDetected");
    if (!risks.isArray())
      throw new ApiException(403, "Screen protection could not be verified. Update Google Play and try again");
    for (var risk : risks) {
      String name = risk.asText();
      if (name.endsWith("_CAPTURING") || name.endsWith("_CONTROLLING") || name.endsWith("_OVERLAYS"))
        throw new ApiException(403, "Close screen capture, remote control and overlay apps before reading");
    }
  }

  private static void deny() {
    throw new ApiException(403, "Use the current Google Play app on a supported, unmodified device");
  }
}
