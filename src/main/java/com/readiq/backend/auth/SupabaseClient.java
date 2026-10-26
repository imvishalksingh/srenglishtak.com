package com.readiq.backend.auth;

import com.readiq.backend.api.ApiException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class SupabaseClient {
  private final String url, key, serviceKey;
  private final ObjectMapper json;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public SupabaseClient(
      @Value("${supabase.url}") String url,
      @Value("${supabase.key}") String key,
      @Value("${supabase.service-role-key}") String serviceKey,
      ObjectMapper json) {
    this.url = url.replaceAll("/+$", "");
    this.key = key;
    this.serviceKey = serviceKey;
    this.json = json;
    URI endpoint = URI.create(this.url);
    if (endpoint.getHost() == null
        || (!"https".equals(endpoint.getScheme())
            && !Set.of("localhost", "127.0.0.1").contains(endpoint.getHost())))
      throw new IllegalArgumentException("SUPABASE_URL must use HTTPS");
  }

  public Map<String, Object> auth(
      String method, String path, Map<String, Object> body, String token) {
    return request(method, "/auth/v1" + path, body, token, false);
  }

  public Map<String, Object> request(
      String method, String path, Map<String, Object> body, String token, boolean admin) {
    try {
      var b =
          HttpRequest.newBuilder(URI.create(url + path))
              .timeout(Duration.ofSeconds(12))
              .header("apikey", admin ? serviceKey : key)
              .header("Content-Type", "application/json");
      if (token != null) b.header("Authorization", "Bearer " + token);
      else if (admin) b.header("Authorization", "Bearer " + serviceKey);
      b.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      var response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        if (response.statusCode() == 429)
          throw new ApiException(429, "Too many authentication attempts");
        if (response.statusCode() >= 500)
          throw new ApiException(503, "Authentication service unavailable");
        throw new ApiException(
            401, "Authentication failed. Check your credentials or provider configuration.");
      }
      if (response.body().isBlank()) return new LinkedHashMap<>();
      return json.readValue(response.body(), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Authentication service unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "Authentication service unavailable");
    }
  }
}
