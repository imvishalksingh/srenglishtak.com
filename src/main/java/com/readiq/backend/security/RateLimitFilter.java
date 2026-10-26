package com.readiq.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shared fixed-window counters; forwarding headers are trusted only at configured ingress
 * addresses.
 */
public class RateLimitFilter extends OncePerRequestFilter {
  private final Set<String> trusted;
  private final RateLimitStore store;

  public RateLimitFilter(String proxies, RateLimitStore store) {
    trusted = new HashSet<>(Arrays.stream(proxies.split(",")).map(String::trim).toList());
    this.store = store;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    if ("OPTIONS".equals(req.getMethod())
        || ("GET".equals(req.getMethod())
            && Set.of("/", "/health").contains(req.getRequestURI()))) {
      chain.doFilter(req, res);
      return;
    }
    String path = req.getRequestURI(), ip = req.getRemoteAddr();
    if (trusted.contains(ip)) {
      String forwarded = req.getHeader("X-Forwarded-For");
      if (forwarded != null) {
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
          String candidate = hops[i].trim();
          if (!candidate.matches("[0-9a-fA-F:.]{2,64}")) break;
          ip = candidate;
          if (!trusted.contains(candidate)) break;
        }
      }
    }
    boolean strict =
        Set.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/google",
                "/api/auth/forgot-password")
            .contains(path);
    String group =
        strict
            ? "auth"
            : path.equals("/api/auth/refresh")
                ? "refresh"
                : path.equals("/api/upload")
                    ? "upload"
                    : path.startsWith("/api/payments/") && !path.endsWith("/webhook")
                        ? "payment"
                        : "api";
    int max =
        strict
            ? 10
            : group.equals("upload")
                ? 20
                : Set.of("payment", "refresh").contains(group) ? 60 : 1000;
    boolean allowed;
    try {
      allowed = store.consume(group + ":" + ip, max, group.equals("upload") ? 60000 : 900000);
    } catch (org.springframework.dao.DataAccessException ex) {
      res.setStatus(503);
      res.setContentType("application/json");
      res.getWriter().write("{\"error\":\"Request protection unavailable\"}");
      return;
    }
    if (!allowed) {
      res.setStatus(429);
      res.setHeader("Retry-After", group.equals("upload") ? "60" : "900");
      res.setContentType("application/json");
      res.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
      return;
    }
    chain.doFilter(req, res);
  }
}
