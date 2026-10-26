package com.readiq.backend.auth;

import java.util.UUID;
import org.springframework.security.core.Authentication;

public record ApiPrincipal(UUID id, boolean admin) {
  public static ApiPrincipal from(Authentication auth) {
    return (ApiPrincipal) auth.getPrincipal();
  }
}
