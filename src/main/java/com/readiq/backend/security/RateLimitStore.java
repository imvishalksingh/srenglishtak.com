package com.readiq.backend.security;

public interface RateLimitStore {
  boolean consume(String key, int maximum, long windowMillis);
}
