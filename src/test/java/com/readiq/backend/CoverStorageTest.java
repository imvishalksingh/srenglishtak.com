package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.service.StorageService;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;

class CoverStorageTest {
  @Test void readsLegacyCoverThroughAuthenticatedStorageWithoutPublishingTheBucket() throws Exception {
    server.createContext("/", exchange -> send(exchange,200,"image-fixture")); server.start();
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/storage/v1/object/public/books/cover.jpg";
    try (var input = service().downloadCover(url)) {
      assertEquals("image-fixture",new String(input.readAllBytes(),StandardCharsets.UTF_8));
    }
    assertEquals(List.of("GET /storage/v1/object/authenticated/books/cover.jpg"),requests);
  }

  @Test void coverDownloadCannotReadDocumentsOrSendCredentialsToOtherHosts() {
    String root = "http://127.0.0.1:" + server.getAddress().getPort();
    for (String location : List.of(root+"/storage/v1/object/public/books/book.pdf",
        root+"/storage/v1/object/authenticated/book-files/secret.jpg",
        "https://external.example/storage/v1/object/public/books/cover.jpg",
        root+"/storage/v1/object/public/books/%2e%2e/cover.jpg",
        root+"/storage/v1/object/public/books/cover.jpg?token=secret"))
      assertThrows(ApiException.class,() -> service().downloadCover(location));
    assertTrue(requests.isEmpty());
  }

  @Test void coverDownloadDoesNotFollowRedirects() throws Exception {
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().set("Location","https://external.example/cover.jpg");
      send(exchange,302,"");
    }); server.start();
    String location = "http://127.0.0.1:" + server.getAddress().getPort()+"/storage/v1/object/public/books/cover.jpg";
    assertThrows(ApiException.class,() -> service().downloadCover(location));
    assertEquals(1,requests.size());
  }
  HttpServer server;
  List<String> requests;
  @BeforeEach void start() throws Exception {
    requests = Collections.synchronizedList(new ArrayList<>());
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  }
  @AfterEach void stop() { server.stop(0); }
  StorageService service() { return new StorageService("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-key"); }
  void send(HttpExchange exchange, int status, String body) throws java.io.IOException {
    requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
    assertEquals("Bearer fixture-key", exchange.getRequestHeaders().getFirst("Authorization"));
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes); exchange.close();
  }
  MockMultipartFile cover() { return new MockMultipartFile("file", "cover.png", "image/png", new byte[] {(byte)137,80,78,71,13,10,26,10,0,0,0,0}); }

  @Test void createsDedicatedImageBucketWithoutChangingLegacyBucket() {
    var created = new AtomicBoolean(false);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      if (path.equals("/storage/v1/bucket/book-covers")) send(exchange, created.get() ? 200 : 400, created.get() ? "{\"public\":true}" : "{\"error\":\"Bucket not found\"}");
      else if (path.equals("/storage/v1/bucket")) {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"id\":\"book-covers\""));
        assertTrue(body.contains("\"public\":true"));
        assertTrue(body.contains("image/png")); assertFalse(body.contains("application/pdf"));
        created.set(true); send(exchange, 200, "{}");
      } else if (path.startsWith("/storage/v1/object/book-covers/")) send(exchange, 200, "{}");
      else send(exchange, 500, "{}");
    }); server.start();
    String url = service().upload(cover(), "public");
    assertTrue(url.contains("/object/public/book-covers/"));
    assertFalse(requests.stream().anyMatch(path -> path.endsWith("/books") || path.contains("/book-files")));
  }

  @Test void refusesToMakeAnExistingPrivateImageBucketPublic() {
    server.createContext("/", exchange -> send(exchange, 200, "{\"public\":false}")); server.start();
    assertThrows(ApiException.class, () -> service().upload(cover(), "public"));
    assertEquals(List.of("GET /storage/v1/bucket/book-covers"), requests);
  }

  @Test void protectedPdfUploadsStillUseOnlyPrivateDocumentStorage() {
    server.createContext("/", exchange -> send(exchange, 200, "{\"public\":false}")); server.start();
    var pdf = new MockMultipartFile("file", "book.pdf", "application/pdf", "%PDF-1.4 fixture".getBytes(StandardCharsets.US_ASCII));
    assertTrue(service().upload(pdf, "book").contains("/object/authenticated/book-files/"));
    assertFalse(requests.stream().anyMatch(path -> path.contains("book-covers")));
  }

  @Test void documentCannotEnterPublicCoverStorage() {
    server.createContext("/", exchange -> send(exchange, 500, "{}")); server.start();
    var pdf = new MockMultipartFile("file", "book.pdf", "application/pdf", "%PDF-1.4 fixture".getBytes(StandardCharsets.US_ASCII));
    assertThrows(ApiException.class, () -> service().upload(pdf, "public"));
    assertTrue(requests.isEmpty());
  }
}
