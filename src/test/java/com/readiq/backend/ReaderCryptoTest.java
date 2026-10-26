package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.reader.ReaderCrypto;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ReaderCryptoTest {
  @Test void copiedProofCannotChangeItsPageOrDeviceKey() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    var pair = generator.generateKeyPair();
    String key = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    var signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(pair.getPrivate());
    signer.update("page-1".getBytes(StandardCharsets.UTF_8));
    String proof = Base64.getEncoder().encodeToString(signer.sign());
    assertDoesNotThrow(() -> ReaderCrypto.verify(key, "page-1", proof));
    assertThrows(ApiException.class, () -> ReaderCrypto.verify(key, "page-2", proof));
    String other = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    assertThrows(ApiException.class, () -> ReaderCrypto.verify(other, "page-1", proof));
    generator.initialize(new ECGenParameterSpec("secp384r1"));
    String wrongCurve = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    assertThrows(ApiException.class, () -> ReaderCrypto.publicKey(wrongCurve));
  }
}
