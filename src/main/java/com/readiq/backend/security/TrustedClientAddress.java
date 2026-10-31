package com.readiq.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** Forwarded addresses are used only after the socket peer matches an explicit trust rule. */
final class TrustedClientAddress {
  private final List<IpAddressMatcher> proxies = new ArrayList<>();

  TrustedClientAddress(String rules) {
    for (String rule : rules.split(",")) {
      rule = rule.trim();
      if (rule.isEmpty()) continue;
      String[] parts = rule.split("/", -1);
      String ip = numericAddress(parts[0]);
      if (ip == null || parts.length > 2) throw invalidRules();
      String suffix = "";
      if (parts.length == 2) {
        try {
          int bits = Integer.parseInt(parts[1]);
          if (bits < 1 || bits > (ip.contains(":") ? 128 : 32)) throw invalidRules();
          suffix = "/" + bits;
        } catch (NumberFormatException invalid) { throw invalidRules(); }
      }
      proxies.add(new IpAddressMatcher(ip + suffix));
    }
  }

  String resolve(HttpServletRequest request) {
    String peer = numericAddress(request.getRemoteAddr());
    if (peer == null || !trusted(peer)) return request.getRemoteAddr();
    List<String> hops = new ArrayList<>();
    var headers = request.getHeaders("X-Forwarded-For");
    int size = 0;
    while (headers.hasMoreElements()) {
      String header = headers.nextElement();
      size += header.length();
      if (size > 4096) return peer;
      for (String value : header.split(",", -1)) {
        String address = numericAddress(value.trim());
        if (address == null || hops.size() >= 32) return peer;
        hops.add(address);
      }
    }
    String client = peer;
    for (int i = hops.size() - 1; i >= 0 && trusted(client); i--) client = hops.get(i);
    return client;
  }

  private boolean trusted(String address) {
    return proxies.stream().anyMatch(rule -> rule.matches(address));
  }

  private static String numericAddress(String value) {
    if (value == null || value.length() > 64) return null;
    // Prevent DNS lookups, zone IDs, ports, shorthand IPv4 and hostnames in client headers.
    if (!(value.contains(":") ? value.matches("[0-9a-fA-F:]+")
        : value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))) return null;
    try { return InetAddress.getByName(value).getHostAddress(); }
    catch (Exception invalid) { return null; }
  }

  private static IllegalArgumentException invalidRules() {
    return new IllegalArgumentException("TRUSTED_PROXY_IPS must contain numeric IPs or explicit CIDRs");
  }
}
