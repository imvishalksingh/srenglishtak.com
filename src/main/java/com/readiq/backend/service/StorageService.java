package com.readiq.backend.service;

import com.readiq.backend.api.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {
  private final String url, key;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public StorageService(
      @Value("${supabase.url}") String url, @Value("${supabase.service-role-key}") String key) {
    this.url = url.replaceAll("/+$", "");
    this.key = key;
  }

  public String upload(MultipartFile file) {
    return upload(file, "public");
  }

  public String upload(MultipartFile file, String purpose) {
    if (!Set.of("book", "public").contains(purpose))
      throw ApiException.bad("Invalid upload purpose");
    if (file.isEmpty()) throw ApiException.bad("No file uploaded");
    if (file.getSize() > 50L * 1024 * 1024) throw new ApiException(413, "File exceeds 50 MiB");
    String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
    String type =
        switch (ext) {
          case ".pdf" -> "application/pdf";
          case ".epub" -> "application/epub+zip";
          case ".png" -> "image/png";
          case ".jpg", ".jpeg" -> "image/jpeg";
          case ".webp" -> "image/webp";
          case ".gif" -> "image/gif";
          default -> throw ApiException.bad("Unsupported file type");
        };
    try {
      byte[] bytes = file.getBytes();
      if (!signature(ext, bytes))
        throw ApiException.bad("File contents do not match the selected file type");
      // No public-document upload escape hatch. Public uploads are cover/other images only.
      if ((ext.equals(".pdf") || ext.equals(".epub")) && !purpose.equals("book"))
        throw ApiException.bad("Documents must be uploaded to private book storage");
      if (purpose.equals("book") && !ext.equals(".pdf"))
        throw ApiException.bad("Protected books must be uploaded as PDF");
      boolean document = purpose.equals("book");
      String bucket = document ? "book-files" : "book-covers";
      if (document) requirePrivateBucket();
      else requirePublicCoverBucket();
      String object = UUID.randomUUID() + ext;
      var request =
          HttpRequest.newBuilder(URI.create(url + "/storage/v1/object/" + bucket + "/" + object))
              .timeout(Duration.ofSeconds(60))
              .header("Authorization", "Bearer " + key)
              .header("apikey", key)
              .header("Content-Type", type)
              .header("x-upsert", "false")
              .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() != 200 && response.statusCode() != 201)
        throw new ApiException(502, "Storage upload failed");
      return url
          + "/storage/v1/object/"
          + (document ? "authenticated/book-files/" : "public/book-covers/")
          + object;
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(502, "Storage upload failed");
    } catch (Exception ex) {
      throw new ApiException(502, "Storage upload failed");
    }
  }

  private boolean signature(String ext, byte[] b) {
    if (b.length < 12) return false;
    String prefix = new String(b, 0, Math.min(12, b.length), StandardCharsets.ISO_8859_1);
    return switch (ext) {
      case ".pdf" -> prefix.startsWith("%PDF-");
      case ".epub" -> validEpub(b);
      case ".png" ->
          Arrays.equals(Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      case ".jpg", ".jpeg" -> b[0] == (byte) 255 && b[1] == (byte) 216 && b[2] == (byte) 255;
      case ".gif" -> prefix.startsWith("GIF87a") || prefix.startsWith("GIF89a");
      case ".webp" -> prefix.startsWith("RIFF") && prefix.substring(8).equals("WEBP");
      default -> false;
    };
  }

  private boolean validEpub(byte[] bytes) {
    boolean mimetype = false, container = false;
    long expanded = 0;
    int entries = 0;
    try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
      java.util.zip.ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > 3000
            || entry.getName().startsWith("/")
            || entry.getName().contains("..")
            || entry.getName().contains("\\")) return false;
        var small = new ByteArrayOutputStream();
        int read;
        while ((read = zip.read(buffer)) != -1) {
          expanded += read;
          if (expanded > 100L * 1024 * 1024) return false;
          if (entry.getName().equals("mimetype")) {
            if (small.size() + read > 100) return false;
            small.write(buffer, 0, read);
          }
        }
        if (entry.getName().equals("mimetype"))
          mimetype = "application/epub+zip".equals(small.toString(StandardCharsets.US_ASCII));
        if (entry.getName().equals("META-INF/container.xml")) container = true;
      }
      return mimetype && container;
    } catch (IOException ex) {
      return false;
    }
  }

  public void requirePrivateFile(String value) {
    if (!privateFile(value)) throw ApiException.bad("Invalid private book location");
    try {
      var req =
          HttpRequest.newBuilder(URI.create(value))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + key)
              .header("apikey", key)
              .method("HEAD", HttpRequest.BodyPublishers.noBody())
              .build();
      var response = client.send(req, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() != 200)
        throw ApiException.bad("Upload the private book file before publishing it");
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Private storage unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "Private storage unavailable");
    }
  }

  public boolean privateFile(String value) {
    if (value == null) return false;
    try {
      URI base = URI.create(url), file = URI.create(value);
      return Objects.equals(base.getScheme(), file.getScheme())
          && Objects.equals(base.getHost(), file.getHost())
          && base.getPort() == file.getPort()
          && file.getUserInfo() == null
          && file.getRawQuery() == null
          && file.getRawFragment() == null
          && file.getPath().startsWith("/storage/v1/object/authenticated/book-files/")
          && !file.getPath().contains("..")
          && !file.getRawPath().contains("%");
    } catch (Exception ex) {
      return false;
    }
  }

  public void requirePublicCopyRetired(String oldUrl) {
    if (oldUrl == null || oldUrl.isBlank() || privateFile(oldUrl)) return;
    URI base = URI.create(url), old;
    try {
      old = URI.create(oldUrl);
    } catch (Exception ex) {
      throw ApiException.bad("Remove the old public book copy before selling it");
    }
    if (!Objects.equals(base.getScheme(), old.getScheme())
        || !Objects.equals(base.getHost(), old.getHost())
        || base.getPort() != old.getPort()
        || old.getUserInfo() != null
        || old.getRawQuery() != null
        || old.getRawPath().contains("%")
        || old.getPath().contains("..")
        || !old.getPath().startsWith("/storage/v1/object/public/books/"))
      throw ApiException.bad(
          "Previously public books need their old public copies retired before being sold");
    try {
      var response =
          client.send(
              HttpRequest.newBuilder(old)
                  .timeout(Duration.ofSeconds(10))
                  .method("HEAD", HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() != 404 && response.statusCode() != 403)
        throw ApiException.bad(
            "Delete the old public book file from Storage before making this book paid");
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Storage check unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "Storage check unavailable");
    }
  }

  // Never make the legacy books bucket public: it may contain old document copies.
  private void requirePublicCoverBucket() {
    try {
      var request = HttpRequest.newBuilder(URI.create(url + "/storage/v1/bucket/book-covers"))
          .timeout(Duration.ofSeconds(10))
          .header("Authorization", "Bearer " + key).header("apikey", key).GET().build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
      var details = mapper.readTree(response.body());
      if (response.statusCode() == 404 || (response.statusCode() == 400
          && "Bucket not found".equals(details.path("error").asText()))) {
        var create = HttpRequest.newBuilder(URI.create(url + "/storage/v1/bucket"))
            .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + key)
            .header("apikey", key).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"id\":\"book-covers\",\"name\":\"book-covers\",\"public\":true,"
                + "\"file_size_limit\":52428800,\"allowed_mime_types\":[\"image/png\",\"image/jpeg\",\"image/webp\",\"image/gif\"]}"))
            .build();
        // Re-read after creation, including races where another upload created it first.
        client.send(create, HttpResponse.BodyHandlers.discarding());
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
      }
      var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
      if (response.statusCode() != 200 || !body.path("public").asBoolean(false))
        throw new ApiException(503, "Public cover image storage is not configured");
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Cover image storage unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "Cover image storage unavailable");
    }
  }

  public void requirePrivateBucket() {
    try {
      var req =
          HttpRequest.newBuilder(URI.create(url + "/storage/v1/bucket/book-files"))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + key)
              .header("apikey", key)
              .GET()
              .build();
      var response = client.send(req, HttpResponse.BodyHandlers.ofString());
      var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
      if (response.statusCode() != 200
          || !body.has("public")
          || body.path("public").asBoolean(true))
        throw new ApiException(503, "Private book storage is not configured");
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Private book storage unavailable");
    } catch (Exception ex) {
      throw new ApiException(503, "Private book storage unavailable");
    }
  }

  public InputStream download(String publicUrl) {
    return downloadObject(publicUrl, false);
  }

  public InputStream downloadCover(String publicUrl) {
    return downloadObject(publicUrl, true);
  }

  private InputStream downloadObject(String publicUrl, boolean cover) {
    URI configured = URI.create(url), file;
    try {
      file = URI.create(publicUrl);
    } catch (Exception ex) {
      throw ApiException.bad("Invalid stored file URL");
    }
    // Service credentials are sent only to our own Supabase bucket. No redirects or arbitrary URLs.
    if (!Objects.equals(configured.getScheme(), file.getScheme())
        || !Objects.equals(configured.getHost(), file.getHost())
        || configured.getPort() != file.getPort()
        || file.getRawFragment() != null
        || file.getRawQuery() != null
        || file.getUserInfo() != null
        || !(cover
            ? (file.getPath().startsWith("/storage/v1/object/public/books/")
                || file.getPath().startsWith("/storage/v1/object/authenticated/books/")
                || file.getPath().startsWith("/storage/v1/object/public/book-covers/")
                || file.getPath().startsWith("/storage/v1/object/authenticated/book-covers/"))
            : (file.getPath().startsWith("/storage/v1/object/public/books/")
                || file.getPath().startsWith("/storage/v1/object/authenticated/book-files/")))
        || file.getPath().contains("..")
        || file.getRawPath().contains("%"))
      throw ApiException.bad("File cannot be streamed from this storage location");
    if (cover && !file.getPath().toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|gif|webp)$"))
      throw ApiException.bad("Invalid cover image location");
    try {
      String path = file.getPath().replace("/object/public/", "/object/authenticated/");
      if (cover) {
        var connection = (java.net.HttpURLConnection) URI.create(url + path).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setRequestProperty("apikey", key);
        try {
          if (connection.getResponseCode() != 200) throw new ApiException(502, "Cover image unavailable");
          return new FilterInputStream(connection.getInputStream()) {
            @Override public void close() throws IOException {
              try { super.close(); } finally { connection.disconnect(); }
            }
          };
        } catch (Exception failure) {
          connection.disconnect();
          throw failure;
        }
      }
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create(url + path))
                  .timeout(Duration.ofSeconds(60))
                  .header("Authorization", "Bearer " + key)
                  .header("apikey", key)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        response.body().close();
        throw new ApiException(502, "File download failed");
      }
      return response.body();
    } catch (ApiException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(502, "File download failed");
    } catch (Exception ex) {
      throw new ApiException(502, "File download failed");
    }
  }
}
