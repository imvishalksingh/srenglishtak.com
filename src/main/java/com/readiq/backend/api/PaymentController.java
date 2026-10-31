package com.readiq.backend.api;

import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.payment.PurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {
  public record GooglePurchase(UUID book_id, @NotBlank @Size(max = 8192) String purchase_token) {}

  public record OrderRequest(@NotNull UUID book_id, @NotNull UUID request_key) {}

  public record VerifyRequest(
      @NotNull UUID order_id,
      @NotBlank @Size(max = 100) String payment_id,
      @NotBlank @Size(max = 64) String signature) {}

  private final PurchaseService service;

  public PaymentController(PurchaseService service) {
    this.service = service;
  }

  @GetMapping("/payments/context")
  public PurchaseService.BillingContext context(Authentication a) {
    return service.context(ApiPrincipal.from(a).id());
  }

  @GetMapping("/user/purchased-books")
  public Object purchasedBooks(Authentication a) {
    return service.purchasedBooks(ApiPrincipal.from(a).id());
  }

  @GetMapping("/user/purchases")
  public Object purchases(Authentication a) {
    return service.purchases(ApiPrincipal.from(a).id());
  }

  @GetMapping("/books/{id}/access")
  public PurchaseService.Access access(@PathVariable UUID id, Authentication a) {
    var p = ApiPrincipal.from(a);
    return service.access(p.id(), id, p.admin(), true);
  }

  @PostMapping("/payments/google/status")
  public Object googleStatus(Authentication a, @Valid @RequestBody GooglePurchase body) {
    return service.googleState(ApiPrincipal.from(a).id(),body.book_id(),body.purchase_token());
  }

  @PostMapping("/payments/google/verify")
  public Object google(Authentication a, @Valid @RequestBody GooglePurchase body) {
    return service.verifyGoogle(ApiPrincipal.from(a).id(), body.book_id(), body.purchase_token());
  }

  @PostMapping("/payments/razorpay/orders")
  public ResponseEntity<PurchaseService.Checkout> order(
      Authentication a, @Valid @RequestBody OrderRequest body) {
    return ResponseEntity.status(201)
        .body(service.createOrder(ApiPrincipal.from(a).id(), body.book_id(), body.request_key()));
  }

  @PostMapping("/payments/razorpay/verify")
  public Object verify(Authentication a, @Valid @RequestBody VerifyRequest body) {
    return service.verifyRazorpay(
        ApiPrincipal.from(a).id(), body.order_id(), body.payment_id(), body.signature());
  }

  @PostMapping(value = "/payments/razorpay/webhook", consumes = "application/json")
  public Object webhook(
      @RequestBody byte[] body, @RequestHeader("X-Razorpay-Signature") String signature) {
    service.webhook(body, signature);
    return Map.of("received", true);
  }
}
