package com.readiq.backend.api;

import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.service.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class StorageController {
  private final com.readiq.backend.payment.PurchaseService purchases;
  private final StorageService storage;
  private final CatalogService catalog;

  public StorageController(
      StorageService storage,
      CatalogService catalog,
      com.readiq.backend.payment.PurchaseService purchases) {
    this.storage = storage;
    this.catalog = catalog;
    this.purchases = purchases;
  }

  @PostMapping("/api/upload")
  @PreAuthorize("hasRole('ADMIN')")
  public Object upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "public") String purpose) {
    return Map.of("url", storage.upload(file, purpose));
  }

  // Retired for every caller, including purchasers and admins. Never add a raw-file fallback.
  @GetMapping("/api/books/{id}/stream")
  public ResponseEntity<?> stream(@PathVariable UUID id, Authentication a) {
    return ResponseEntity.status(410).header("Cache-Control", "private, no-store")
        .body(Map.of("error", "Original book downloads are disabled. Update the app to use the protected reader"));
  }

  @GetMapping({"/", "/health"})
  public Object health() {
    return Map.of("status", "ok", "service", "ReadIQ Spring API");
  }
}
