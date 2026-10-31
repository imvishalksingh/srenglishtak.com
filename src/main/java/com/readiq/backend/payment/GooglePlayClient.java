package com.readiq.backend.payment;

import com.google.auth.oauth2.GoogleCredentials;
import com.readiq.backend.auth.ServiceAccountLoader;
import com.readiq.backend.api.ApiException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

/**
 * Credentials and package name come only from server configuration. Non-consumable purchases are
 * never consumed.
 */
@Component
public class GooglePlayClient {
  private final PaymentSettings settings;
  private final ObjectMapper json;
  private GoogleCredentials credentials;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public GooglePlayClient(PaymentSettings settings, ObjectMapper json) {
    this.settings = settings;
    this.json = json;
  }

  @jakarta.annotation.PostConstruct
  public void validateConfiguration() {
    if (!settings.googleEnabled()) return;
    try {
      if (Base64.getDecoder().decode(settings.getTokenEncryptionKey()).length != 32)
        throw new IllegalArgumentException();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "PAYMENT_TOKEN_ENCRYPTION_KEY must be base64 of 32 bytes; preserve the existing production key");
    }
    credentials = ServiceAccountLoader.load(settings.getGoogleCredentialsFile(),
        settings.getGoogleCredentialsJson(), "https://www.googleapis.com/auth/androidpublisher");
  }

  public JsonNode purchase(String token) {
    return request("GET", "/purchases/productsv2/tokens/" + path(token));
  }

  public void acknowledge(String product, String token) {
    request(
        "POST", "/purchases/products/" + path(product) + "/tokens/" + path(token) + ":acknowledge");
  }

  private synchronized String bearer() throws Exception {
    settings.requireGoogle();
    if (credentials == null) {
      credentials = ServiceAccountLoader.load(settings.getGoogleCredentialsFile(),
          settings.getGoogleCredentialsJson(), "https://www.googleapis.com/auth/androidpublisher");
    }
    credentials.refreshIfExpired();
    return credentials.getAccessToken().getTokenValue();
  }

  private JsonNode request(String method, String path) {
    try {
      String url =
          "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
              + path(settings.getGooglePackageName())
              + path;
      var req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Bearer " + bearer())
              .header("Content-Type", "application/json")
              .method(
                  method,
                  method.equals("POST")
                      ? HttpRequest.BodyPublishers.ofString("{}")
                      : HttpRequest.BodyPublishers.noBody())
              .build();
      var response = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404 || response.statusCode() == 410)
        throw new ApiException(422, "Purchase does not exist or is no longer valid");
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new ApiException(503, "Google Play verification unavailable");
      return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Google Play verification unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "Google Play verification unavailable");
    }
  }

  private static String path(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
