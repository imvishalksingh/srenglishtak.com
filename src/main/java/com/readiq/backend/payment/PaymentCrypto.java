package com.readiq.backend.payment;

import com.readiq.backend.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

@Component
public class PaymentCrypto {
  private final PaymentSettings settings;

  public PaymentCrypto(PaymentSettings settings) {
    this.settings = settings;
  }

  public static boolean signature(byte[] data, String secret, String supplied) {
    if (secret == null
        || secret.isBlank()
        || supplied == null
        || !supplied.matches("[a-fA-F0-9]{64}")) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return MessageDigest.isEqual(mac.doFinal(data), HexFormat.of().parseHex(supplied));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String encrypt(String token) {
    try {
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
      byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
      byte[] result = new byte[nonce.length + encrypted.length];
      System.arraycopy(nonce, 0, result, 0, nonce.length);
      System.arraycopy(encrypted, 0, result, 12, encrypted.length);
      return Base64.getEncoder().encodeToString(result);
    } catch (Exception ex) {
      throw new ApiException(503, "Purchase token encryption is not configured correctly");
    }
  }

  public String decrypt(String value) {
    try {
      byte[] bytes = Base64.getDecoder().decode(value);
      return new String(
          cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(bytes, 12))
              .doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)),
          StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new ApiException(503, "Purchase token decryption failed");
    }
  }

  private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
    byte[] key = Base64.getDecoder().decode(settings.getTokenEncryptionKey());
    if (key.length != 32)
      throw new IllegalArgumentException(
          "PAYMENT_TOKEN_ENCRYPTION_KEY must be base64 of 32 random bytes");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
    cipher.updateAAD("readiq-google-token-v1".getBytes(StandardCharsets.UTF_8));
    return cipher;
  }
}
