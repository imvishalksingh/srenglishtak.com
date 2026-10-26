package com.readiq.backend.api;

import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.service.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

  @GetMapping("/api/books/{id}/stream")
  public ResponseEntity<StreamingResponseBody> stream(@PathVariable UUID id, Authentication a) {
    var principal = ApiPrincipal.from(a);
    purchases.requireAccess(principal.id(), id, principal.admin());
    // The actual storage URL never leaves the server for ordinary clients.
    var b = catalog.book(id, true);
    if (((Number) b.getOrDefault("price_paise", 0)).intValue() > 0) {
      if (!storage.privateFile(Objects.toString(b.get("file_url"), null)))
        throw new ApiException(503, "Paid book storage is not private");
      storage.requirePrivateBucket();
    }
    if (b.get("file_url") == null) throw ApiException.missing();
    String type = "epub".equals(b.get("format_type")) ? "application/epub+zip" : "application/pdf";
    var input = storage.download(b.get("file_url").toString());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(type))
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            "inline; filename=\"book_"
                + id
                + ("application/pdf".equals(type) ? ".pdf" : ".epub")
                + "\"")
        .body(
            output -> {
              try (input) {
                input.transferTo(output);
              }
            });
  }

  @GetMapping({"/", "/health"})
  public Object health() {
    return Map.of("status", "ok", "service", "ReadIQ Spring API");
  }
}
