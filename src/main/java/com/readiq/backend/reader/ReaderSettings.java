package com.readiq.backend.reader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record ReaderSettings(
    @Value("${reader.cloud-project-number:0}") long cloudProjectNumber,
    @Value("${reader.credentials-file:${payments.google-credentials-file:}}") String credentialsFile,
    @Value("${reader.credentials-json:}") String credentialsJson,
    @Value("${reader.package-name:${payments.google-package-name:com.srenglishtak.app}}") String packageName,
    @Value("${reader.certificate-sha256:}") String certificateSha256,
    @Value("${reader.minimum-version-code:10}") long minimumVersionCode) {
  @org.springframework.beans.factory.annotation.Autowired
  public ReaderSettings {}

  // Retain the existing source constructor for integrations using a mounted file.
  public ReaderSettings(long cloudProjectNumber, String credentialsFile, String packageName,
      String certificateSha256, long minimumVersionCode) {
    this(cloudProjectNumber, credentialsFile, "", packageName, certificateSha256, minimumVersionCode);
  }

  public boolean hasCredentials() {
    return !credentialsFile.isBlank() || !credentialsJson.isBlank();
  }

  @Override public String toString() { return "ReaderSettings[credentials=REDACTED]"; }
}
