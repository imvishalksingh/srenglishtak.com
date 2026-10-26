package com.readiq.backend.reader;

import com.google.auth.oauth2.*;
import com.readiq.backend.api.ApiException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

/** Google decrypts and verifies the token. Client-supplied verdict JSON is never accepted. */
@Component
public class PlayIntegrityClient {
  private final ReaderSettings settings;
  private final ObjectMapper json;
  private GoogleCredentials credentials;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

  public PlayIntegrityClient(ReaderSettings settings, ObjectMapper json) {
    this.settings = settings;
    this.json = json;
  }

  private synchronized String bearer() throws Exception {
    if (credentials == null) {
      try (var input = Files.newInputStream(Path.of(settings.credentialsFile()))) {
        credentials = ServiceAccountCredentials.fromStream(input)
            .createScoped("https://www.googleapis.com/auth/playintegrity");
      }
    }
    credentials.refreshIfExpired();
    return credentials.getAccessToken().getTokenValue();
  }

  public JsonNode decode(String token) {
    if (token == null || token.isBlank() || token.length() > 50000)
      throw ApiException.bad("A Play Integrity token is required");
    try {
      var request = HttpRequest.newBuilder(URI.create("https://playintegrity.googleapis.com/v1/"
              + settings.packageName() + ":decodeIntegrityToken"))
          .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + bearer())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
              java.util.Map.of("integrity_token", token)))).build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var input = response.body()) {
        if (response.statusCode() == 400) throw new ApiException(403, "App integrity check failed");
        if (response.statusCode() != 200) throw new ApiException(503, "App verification is unavailable");
        byte[] body = input.readNBytes(262145);
        if (body.length > 262144) throw new ApiException(503, "App verification is unavailable");
        return json.readTree(body).path("tokenPayloadExternal");
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "App verification is unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "App verification is unavailable");
    }
  }
}
