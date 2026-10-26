package com.readiq.backend.payment;

import com.readiq.backend.api.ApiException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class RazorpayClient {
  private final PaymentSettings settings;
  private final ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public RazorpayClient(PaymentSettings settings, ObjectMapper json) {
    this.settings = settings;
    this.json = json;
  }

  public JsonNode createOrder(UUID localId, int amount) {
    return request(
        "POST",
        "/orders",
        Map.of("amount", amount, "currency", "INR", "receipt", localId.toString()));
  }

  public JsonNode payment(String id) {
    return request("GET", "/payments/" + identifier(id), null);
  }

  public JsonNode order(String id) {
    return request("GET", "/orders/" + identifier(id), null);
  }

  private String identifier(String value) {
    if (value == null || !value.matches("[A-Za-z0-9_]{5,100}"))
      throw ApiException.bad("Invalid payment identifier");
    return value;
  }

  private JsonNode request(String method, String path, Object body) {
    settings.requireRazorpay();
    try {
      String auth =
          Base64.getEncoder()
              .encodeToString(
                  (settings.getRazorpayKeyId() + ":" + settings.getRazorpayKeySecret())
                      .getBytes(StandardCharsets.UTF_8));
      var req =
          HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1" + path))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Basic " + auth)
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new ApiException(503, "Payment provider unavailable");
      return json.readTree(response.body());
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Payment provider unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "Payment provider unavailable");
    }
  }
}
