package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.payment.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentCryptoTest {
  @Test
  void encryptedPurchaseTokensRejectTamperingAndTheWrongKey() {
    var settings = new PaymentSettings();
    settings.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
    var crypto = new PaymentCrypto(settings);
    String first = crypto.encrypt("private-purchase-token"),
        second = crypto.encrypt("private-purchase-token");
    assertNotEquals(first, second);
    assertEquals("private-purchase-token", crypto.decrypt(first));
    byte[] corrupted = Base64.getDecoder().decode(first);
    corrupted[corrupted.length - 1] ^= 1;
    assertThrows(
        ApiException.class, () -> crypto.decrypt(Base64.getEncoder().encodeToString(corrupted)));
    byte[] different = new byte[32];
    Arrays.fill(different, (byte) 1);
    settings.setTokenEncryptionKey(Base64.getEncoder().encodeToString(different));
    assertThrows(ApiException.class, () -> crypto.decrypt(first));
  }

  @Test
  void invalidServerEncryptionConfigurationCannotEnableARealGoogleClient() {
    var settings = new PaymentSettings();
    settings.setMode("test");
    settings.setGoogleCredentialsFile("not-a-real-key.json");
    settings.setTokenEncryptionKey("invalid");
    var google =
        new GooglePlayClient(settings, tools.jackson.databind.json.JsonMapper.builder().build());
    assertThrows(IllegalStateException.class, google::validateConfiguration);
  }
}
