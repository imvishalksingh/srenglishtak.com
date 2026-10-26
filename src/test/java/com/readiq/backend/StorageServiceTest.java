package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.service.StorageService;
import org.junit.jupiter.api.Test;

class StorageServiceTest {
  @Test
  void streamingNeverSendsServiceCredentialsToAnArbitraryHost() {
    var service = new StorageService("https://project.supabase.co", "not-a-real-key");
    assertThrows(
        ApiException.class,
        () -> service.download("https://attacker.example/storage/v1/object/public/books/file.pdf"));
    assertThrows(
        ApiException.class,
        () ->
            service.download(
                "https://project.supabase.co/storage/v1/object/public/books/../secret"));
    assertThrows(
        ApiException.class,
        () ->
            service.download(
                "https://project.supabase.co/storage/v1/object/public/books/%2e%2e/file"));
  }
}
