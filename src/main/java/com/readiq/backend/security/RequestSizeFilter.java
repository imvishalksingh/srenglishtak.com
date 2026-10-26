package com.readiq.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bound JSON input even when Content-Length is absent (chunked requests). Multipart has a separate
 * 50 MiB limit.
 */
public class RequestSizeFilter extends OncePerRequestFilter {
  static final long MAX = 1024 * 1024;

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getContentType() == null
        || !req.getContentType().toLowerCase(java.util.Locale.ROOT).contains("application/json")) {
      chain.doFilter(req, res);
      return;
    }
    if (req.getContentLengthLong() > MAX) {
      res.setStatus(413);
      res.setContentType("application/json");
      res.getWriter().write("{\"error\":\"JSON request exceeds 1 MiB\"}");
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(req) {
          @Override
          public ServletInputStream getInputStream() throws IOException {
            ServletInputStream source = req.getInputStream();
            return new ServletInputStream() {
              long read;

              private void check(int n) throws IOException {
                if (n > 0 && (read += n) > MAX) throw new IOException("JSON request too large");
              }

              @Override
              public int read() throws IOException {
                int value = source.read();
                check(value < 0 ? 0 : 1);
                return value;
              }

              @Override
              public int read(byte[] b, int offset, int length) throws IOException {
                int n = source.read(b, offset, length);
                check(n);
                return n;
              }

              @Override
              public boolean isFinished() {
                return source.isFinished();
              }

              @Override
              public boolean isReady() {
                return source.isReady();
              }

              @Override
              public void setReadListener(ReadListener listener) {
                source.setReadListener(listener);
              }
            };
          }
        },
        res);
  }
}
