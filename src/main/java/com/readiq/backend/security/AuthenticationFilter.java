package com.readiq.backend.security;

import com.readiq.backend.api.*;
import com.readiq.backend.auth.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthenticationFilter extends OncePerRequestFilter {
  private final AuthService auth;

  public AuthenticationFilter(AuthService auth) {
    this.auth = auth;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String h = req.getHeader("Authorization");
    if (h != null) {
      if (!h.startsWith("Bearer ") || h.length() > 24000) {
        fail(res, 401);
        return;
      }
      try {
        var p = auth.authenticate(h.substring(7));
        boolean admin = "admin".equals(p.get("role"));
        var principal = new ApiPrincipal(Input.uuid(p.get("id")), admin);
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER"))));
      } catch (ApiException ex) {
        fail(res, ex.status());
        return;
      }
    }
    chain.doFilter(req, res);
  }

  private void fail(HttpServletResponse res, int status) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.getWriter()
        .write(
            "{\"error\":\""
                + (status == 503
                    ? "Authentication service unavailable"
                    : "Missing or invalid token")
                + "\"}");
  }
}
