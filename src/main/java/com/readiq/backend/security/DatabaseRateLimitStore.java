package com.readiq.backend.security;

import com.readiq.backend.auth.AccessTokenVerifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Atomic PostgreSQL counters coordinate all replicas; raw IP addresses are not stored. */
@Component
public class DatabaseRateLimitStore implements RateLimitStore {
  private final JdbcTemplate jdbc;

  public DatabaseRateLimitStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean consume(String key, int maximum, long windowMillis) {
    Integer used =
        jdbc.queryForObject(
            """
            insert into api_rate_limits(bucket_key,window_start,used,expires_at)
            values(?,floor(extract(epoch from clock_timestamp())*1000/?)::bigint,1,
                now()+(? * interval '1 millisecond'))
            on conflict(bucket_key) do update set
              used=case when api_rate_limits.window_start=excluded.window_start
                   then least(api_rate_limits.used+1,?) else 1 end,
              expires_at=excluded.expires_at,window_start=excluded.window_start
            returning used
            """,
            Integer.class,
            AccessTokenVerifier.hash(key),
            windowMillis,
            windowMillis,
            maximum + 1);
    return used != null && used <= maximum;
  }

  @Scheduled(
      initialDelayString = "${security.rate-limit-cleanup-ms:300000}",
      fixedDelayString = "${security.rate-limit-cleanup-ms:300000}")
  public void cleanup() {
    jdbc.update("delete from api_rate_limits where expires_at<now()");
    jdbc.update("delete from revoked_access_tokens where expires_at<now()");
  }
}
