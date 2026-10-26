package com.readiq.backend.reader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record ReaderSettings(
    @Value("${reader.cloud-project-number:0}") long cloudProjectNumber,
    @Value("${reader.credentials-file:${payments.google-credentials-file:}}") String credentialsFile,
    @Value("${reader.package-name:${payments.google-package-name:com.srenglishtak.app}}") String packageName,
    @Value("${reader.certificate-sha256:}") String certificateSha256,
    @Value("${reader.minimum-version-code:10}") long minimumVersionCode) {}
