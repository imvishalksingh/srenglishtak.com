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

  /** A duplicate signed nonce cannot spend another quota slot, even across replicas. */
  public String consumePage(java.util.UUID session, java.util.UUID nonce, String key, int maximum, long windowMillis) {
    return jdbc.queryForObject("""
        with proof as (
          insert into reader_request_nonces(session_id,nonce,expires_at)
          values(?,?,now()+interval '60 seconds') on conflict do nothing returning nonce
        ), quota as (
          insert into api_rate_limits(bucket_key,window_start,used,expires_at)
          select ?,floor(extract(epoch from clock_timestamp())*1000/?)::bigint,1,
            now()+(? * interval '1 millisecond') from proof
          on conflict(bucket_key) do update set
            used=case when api_rate_limits.window_start=excluded.window_start
                 then least(api_rate_limits.used+1,?) else 1 end,
            expires_at=excluded.expires_at,window_start=excluded.window_start
          returning used
        ) select case when not exists(select 1 from proof) then 'replay'
          when (select used from quota)>? then 'limited' else 'allowed' end
        """,String.class,session,nonce,AccessTokenVerifier.hash(key),windowMillis,windowMillis,maximum+1,maximum);
  }

  @Scheduled(
      initialDelayString = "${security.rate-limit-cleanup-ms:300000}",
      fixedDelayString = "${security.rate-limit-cleanup-ms:300000}")
  public void cleanup() {
    jdbc.update("delete from api_rate_limits where expires_at<now()");
    jdbc.update("delete from revoked_access_tokens where expires_at<now()");
  }
}
