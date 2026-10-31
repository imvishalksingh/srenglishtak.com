package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.readiq.backend.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RateLimitFilterTest {
  private String key(String rules, String peer, String... headers) throws Exception {
    var captured = new java.util.concurrent.atomic.AtomicReference<String>();
    var filter = new RateLimitFilter(rules, (key, max, window) -> { captured.set(key); return true; });
    var request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setRemoteAddr(peer);
    for (String header : headers) request.addHeader("X-Forwarded-For", header);
    filter.doFilter(request, new MockHttpServletResponse(), (q, r) -> {});
    return captured.get();
  }

  @Test void walksAllForwardedHeadersFromRightToLeftThroughKnownProxies() throws Exception {
    assertEquals("auth:198.51.100.20", key("10.42.0.0/24", "10.42.0.5",
        "192.0.2.66, 198.51.100.20", "10.42.0.6"));
    assertEquals("auth:198.51.100.20", key("10.42.0.5", "10.42.0.5", "198.51.100.20"));
  }

  @Test void unknownPeerAndUnknownHopNeverTrustSpoofedClientAddress() throws Exception {
    assertEquals("auth:10.43.0.5", key("10.42.0.0/24", "10.43.0.5", "192.0.2.66"));
    assertEquals("auth:198.51.100.20", key("10.42.0.5", "10.42.0.5",
        "192.0.2.66, 198.51.100.20"));
  }

  @Test void ipv6IsCanonicalizedBeforeMatching() throws Exception {
    assertEquals("auth:2001:db8:1:0:0:0:0:20", key("2001:db8:42::/64", "2001:db8:42::5",
        "2001:db8:1::20"));
  }

  @Test void malformedOrOversizedHeadersUseSocketPeer() throws Exception {
    for (String header : java.util.List.of("client.example", "198.51.100.2:443", "unknown",
        "192.0.2.66,,198.51.100.20", "1".repeat(5000)))
      assertEquals("auth:10.42.0.5", key("10.42.0.5", "10.42.0.5", header));
  }

  @Test void invalidProxyConfigurationFailsClosed() {
    for (String rule : java.util.List.of("*", "0.0.0.0/0", "::/0", "example.test", "10.0.0.1/33"))
      assertThrows(IllegalArgumentException.class, () -> new RateLimitFilter(rule, (k, m, w) -> true));
  }

  @Test void probesNeverConsumeDatabaseRateLimits() throws Exception {
    var filter = new RateLimitFilter("", (key, max, window) -> { fail("Probe accessed rate-limit store"); return false; });
    for (String path : java.util.List.of("/health", "/health/live", "/health/ready")) {
      var response = new MockHttpServletResponse();
      filter.doFilter(new MockHttpServletRequest("GET", path), response, (q, r) -> {});
      assertEquals(200, response.getStatus());
    }
  }

  @Test
  void changingForwardedHeaderCannotBypassUntrustedIpLimit() throws Exception {
    var count = new java.util.concurrent.atomic.AtomicInteger();
    var filter = new RateLimitFilter("", (key, max, window) -> count.incrementAndGet() <= max);
    for (int i = 0; i < 11; i++) {
      var req = new MockHttpServletRequest("POST", "/api/auth/login");
      req.setRemoteAddr("192.0.2.1");
      req.addHeader("X-Forwarded-For", "198.51.100." + i);
      var res = new MockHttpServletResponse();
      filter.doFilter(req, res, (q, r) -> r.getWriter().write("allowed"));
      assertEquals(i < 10 ? 200 : 429, res.getStatus());
    }
  }
}
