package com.readiq.backend.api;

import static com.readiq.backend.dto.DtoMapper.body;

import com.readiq.backend.auth.*;
import com.readiq.backend.data.Database;
import com.readiq.backend.dto.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final Database db;

  public AuthController(AuthService auth, Database db) {
    this.auth = auth;
    this.db = db;
  }

  @PostMapping("/login")
  public AuthResponse login(@RequestBody LoginRequest request) {
    var b = body(request);
    return AuthResponse.from(auth.login(b));
  }

  @PostMapping("/google")
  public AuthResponse google(@RequestBody GoogleRequest request) {
    var b = body(request);
    return AuthResponse.from(auth.google(b));
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(@RequestBody RefreshRequest request) {
    var b = body(request);
    return AuthResponse.from(auth.refresh(b));
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    var b = body(request);
    return ResponseEntity.status(201).body(AuthResponse.registration(auth.register(b)));
  }

  @PostMapping("/forgot-password")
  public Object forgot(@RequestBody ForgotRequest request) {
    var b = body(request);
    return auth.forgot(b);
  }

  @PostMapping("/logout")
  public Object logout(@RequestHeader("Authorization") String token) {
    return auth.logout(token.substring(7));
  }

  @GetMapping("/me")
  public AuthResponse.Profile me(Authentication a) {
    return AuthResponse.Profile.from(db.byId("profiles", ApiPrincipal.from(a).id()));
  }
}
