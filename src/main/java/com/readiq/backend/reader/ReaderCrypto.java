package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;

public final class ReaderCrypto {
  private ReaderCrypto() {}

  public static PublicKey publicKey(String encoded) {
    try {
      if (encoded == null || encoded.length() > 256) throw new IllegalArgumentException();
      var key = (ECPublicKey) KeyFactory.getInstance("EC")
          .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
      var parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256r1"));
      var expected = parameters.getParameterSpec(ECParameterSpec.class);
      var actual = key.getParams();
      if (!actual.getCurve().equals(expected.getCurve())
          || !actual.getGenerator().equals(expected.getGenerator())
          || !actual.getOrder().equals(expected.getOrder())
          || actual.getCofactor() != expected.getCofactor()) throw new IllegalArgumentException();
      return key;
    } catch (Exception ex) {
      throw ApiException.bad("Invalid reader device key");
    }
  }

  public static void verify(String key, String message, String encodedSignature) {
    try {
      if (encodedSignature == null || encodedSignature.length() > 128) throw new Exception();
      var signature = Signature.getInstance("SHA256withECDSA");
      signature.initVerify(publicKey(key));
      signature.update(message.getBytes(StandardCharsets.UTF_8));
      if (!signature.verify(Base64.getDecoder().decode(encodedSignature))) throw new Exception();
    } catch (Exception ex) {
      throw new ApiException(403, "Reader device proof is invalid");
    }
  }

  public static String hash(String value) {
    try {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static String proof(String action, UUID book, UUID session, int page, long timestamp, UUID nonce) {
    return action + "\n" + book + "\n" + session + "\n" + page + "\n" + timestamp + "\n" + nonce;
  }
}
