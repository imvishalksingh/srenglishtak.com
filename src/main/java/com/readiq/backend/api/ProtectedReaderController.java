package com.readiq.backend.api;

import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.reader.ProtectedReaderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books/{book}/reader")
public class ProtectedReaderController {
  private final ProtectedReaderService reader;
  public ProtectedReaderController(ProtectedReaderService reader) { this.reader = reader; }

  public record ChallengeRequest(@NotBlank @Size(max=256) String public_key) {}
  public record ActivateRequest(@NotNull UUID challenge_id, @NotBlank @Size(max=50000) String integrity_token,
      @NotBlank @Size(max=128) String signature, @Min(1) @Max(2000) Integer initial_page) {}
  public record ProofRequest(@Positive long timestamp, @NotNull UUID nonce,
      @NotBlank @Size(max=128) String signature) {}

  @PostMapping("/challenge")
  public ResponseEntity<?> challenge(@PathVariable UUID book, @Valid @RequestBody ChallengeRequest body, Authentication a) {
    long started=System.nanoTime();
    return response(reader.challenge(ApiPrincipal.from(a), book, body.public_key()), MediaType.APPLICATION_JSON, started);
  }

  @PostMapping("/sessions")
  public ResponseEntity<?> activate(@PathVariable UUID book, @Valid @RequestBody ActivateRequest body, Authentication a) {
    long started=System.nanoTime();
    return response(reader.activate(ApiPrincipal.from(a), book, body.challenge_id(), body.integrity_token(),
        body.signature(), body.initial_page()), MediaType.APPLICATION_JSON, started);
  }

  @PostMapping("/sessions/{session}/pages/{page}")
  public ResponseEntity<byte[]> page(@PathVariable UUID book, @PathVariable UUID session, @PathVariable int page,
      @Valid @RequestBody ProofRequest body, Authentication a) {
    long started=System.nanoTime();
    return response(reader.page(ApiPrincipal.from(a), book, session, page, body.timestamp(), body.nonce(),
        body.signature()), MediaType.IMAGE_JPEG, started);
  }

  @PostMapping("/sessions/{session}/close")
  public ResponseEntity<?> close(@PathVariable UUID book, @PathVariable UUID session,
      @Valid @RequestBody ProofRequest body, Authentication a) {
    long started=System.nanoTime();
    reader.close(ApiPrincipal.from(a), book, session, body.timestamp(), body.nonce(), body.signature());
    return response(java.util.Map.of("closed", true), MediaType.APPLICATION_JSON, started);
  }

  private static <T> ResponseEntity<T> response(T body, MediaType type, long started) {
    return ResponseEntity.ok().header("Server-Timing", "reader;dur="+((System.nanoTime()-started)/1_000_000)).contentType(type).header("Cache-Control", "private, no-store, max-age=0")
        .header("Pragma", "no-cache").header("X-Content-Type-Options", "nosniff")
        .header("Cross-Origin-Resource-Policy", "same-origin").body(body);
  }
}
