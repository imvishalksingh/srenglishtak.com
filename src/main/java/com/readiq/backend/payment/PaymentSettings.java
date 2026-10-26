package com.readiq.backend.payment;

import com.readiq.backend.api.ApiException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payments")
public class PaymentSettings {
  private String mode = "disabled",
      razorpayKeyId = "",
      razorpayKeySecret = "",
      razorpayWebhookSecret = "";
  private String googleCredentialsFile = "",
      googlePackageName = "com.srenglishtak.app",
      tokenEncryptionKey = "";

  public String getMode() {
    return mode;
  }

  public void setMode(String v) {
    if (!java.util.Set.of("disabled", "test", "live").contains(v))
      throw new IllegalArgumentException("Invalid PAYMENTS_MODE");
    mode = v;
  }

  public String getRazorpayKeyId() {
    return razorpayKeyId;
  }

  public void setRazorpayKeyId(String v) {
    razorpayKeyId = v;
  }

  public String getRazorpayKeySecret() {
    return razorpayKeySecret;
  }

  public void setRazorpayKeySecret(String v) {
    razorpayKeySecret = v;
  }

  public String getRazorpayWebhookSecret() {
    return razorpayWebhookSecret;
  }

  public void setRazorpayWebhookSecret(String v) {
    razorpayWebhookSecret = v;
  }

  public String getGoogleCredentialsFile() {
    return googleCredentialsFile;
  }

  public void setGoogleCredentialsFile(String v) {
    googleCredentialsFile = v;
  }

  public String getGooglePackageName() {
    return googlePackageName;
  }

  public void setGooglePackageName(String v) {
    googlePackageName = v;
  }

  public String getTokenEncryptionKey() {
    return tokenEncryptionKey;
  }

  public void setTokenEncryptionKey(String v) {
    tokenEncryptionKey = v;
  }

  public boolean googleEnabled() {
    return !mode.equals("disabled")
        && !googleCredentialsFile.isBlank()
        && !tokenEncryptionKey.isBlank();
  }

  public boolean razorpayEnabled() {
    return !mode.equals("disabled")
        && !razorpayKeyId.isBlank()
        && !razorpayKeySecret.isBlank()
        && !razorpayWebhookSecret.isBlank();
  }

  public void requireGoogle() {
    if (!googleEnabled()) throw new ApiException(503, "Google Play payments are not configured");
  }

  public void requireRazorpay() {
    if (!razorpayEnabled()) throw new ApiException(503, "Website payments are not configured");
    if (!razorpayKeyId.startsWith(mode.equals("live") ? "rzp_live_" : "rzp_test_"))
      throw new ApiException(503, "Payment key mode mismatch");
  }
}
