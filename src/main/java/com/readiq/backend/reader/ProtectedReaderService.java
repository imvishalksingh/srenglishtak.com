package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.data.Database;
import com.readiq.backend.payment.PurchaseService;
import com.readiq.backend.security.DatabaseRateLimitStore;
import com.readiq.backend.service.CatalogService;
import java.time.Instant;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProtectedReaderService {
  private final Database db;
  private final DatabaseRateLimitStore limits;
  private final CatalogService catalog;
  private final PurchaseService purchases;
  private final BookPageRenderer renderer;
  private final PlayIntegrityVerifier integrity;
  private final ReaderSettings settings;
  private final TransactionTemplate tx;

  public record Challenge(UUID challenge_id, String request_hash, long cloud_project_number) {}
  public record Session(UUID session_id, int page_count, long expires_at) {}

  public ProtectedReaderService(Database db, DatabaseRateLimitStore limits, CatalogService catalog,
      PurchaseService purchases, BookPageRenderer renderer, PlayIntegrityVerifier integrity,
      ReaderSettings settings, PlatformTransactionManager manager) {
    this.db = db; this.limits = limits; this.catalog = catalog; this.purchases = purchases;
    this.renderer = renderer; this.integrity = integrity; this.settings = settings;
    this.tx = new TransactionTemplate(manager);
  }

  public Challenge challenge(ApiPrincipal user, UUID book, String key) {
    purchases.requireAccess(user.id(), book, user.admin());
    integrity.requireConfigured();
    ReaderCrypto.publicKey(key);
    limit("challenge:" + user.id(), 6, 60000);
    UUID id = UUID.randomUUID();
    String hash = ReaderCrypto.hash("reader-v1\n" + user.id() + "\n" + book + "\n" + id + "\n" + key);
    db.execute("insert into reader_challenges(id,user_id,book_id,public_key,request_hash,expires_at)"
        + " values(?,?,?,?,?,now()+interval '90 seconds')", id, user.id(), book, key, hash);
    return new Challenge(id, hash, settings.cloudProjectNumber());
  }

  public Session activate(ApiPrincipal user, UUID book, UUID challenge, String token, String signature) {
    purchases.requireAccess(user.id(), book, user.admin());
    var row = db.optional("select * from reader_challenges where id=? and user_id=? and book_id=?"
            + " and expires_at>now()", challenge, user.id(), book)
        .orElseThrow(() -> new ApiException(403, "Reader challenge expired. Open the book again"));
    String key = row.get("public_key").toString(), hash = row.get("request_hash").toString();
    ReaderCrypto.verify(key, "activate\n" + challenge + "\n" + hash, signature);
    limit("activate:" + user.id(), 6, 60000);
    // Consume before the external call so a replay cannot spend verification/render capacity.
    if (db.execute("delete from reader_challenges where id=? and user_id=? and expires_at>now()",
        challenge, user.id()) != 1) throw new ApiException(403, "Reader challenge was already used");
    integrity.verify(token, hash);
    int count = renderer.pageCount(catalog.book(book, true));
    UUID id = UUID.randomUUID();
    long expiry = Instant.now().plusSeconds(300).getEpochSecond();
    tx.executeWithoutResult(status -> {
      // Account lock coordinates concurrent session creation across replicas.
      db.one("select id from profiles where id=? for update", user.id());
      db.execute("delete from reader_sessions where user_id=?", user.id());
      db.execute("insert into reader_sessions(id,user_id,book_id,public_key,expires_at)"
          + " values(?,?,?,?,to_timestamp(?))", id, user.id(), book, key, expiry);
    });
    return new Session(id, count, expiry);
  }

  public byte[] page(ApiPrincipal user, UUID book, UUID session, int page,
      long timestamp, UUID nonce, String signature) {
    if (page < 1 || page > 2000) throw ApiException.missing();
    authorize(user, book, session, "page", page, timestamp, nonce, signature);
    limit("page:" + user.id(), 30, 60000);
    return renderer.render(catalog.book(book, true), page, user.id(), session);
  }

  public void close(ApiPrincipal user, UUID book, UUID session, long timestamp, UUID nonce, String signature) {
    authorize(user, book, session, "close", 0, timestamp, nonce, signature);
    db.execute("delete from reader_sessions where id=? and user_id=?", session, user.id());
  }

  private void authorize(ApiPrincipal user, UUID book, UUID session, String action, int page,
      long timestamp, UUID nonce, String signature) {
    purchases.requireAccess(user.id(), book, user.admin());
    long now = Instant.now().getEpochSecond();
    if (nonce == null || timestamp < now - 30 || timestamp > now + 10)
      throw new ApiException(403, "Reader request expired");
    var row = db.optional("select public_key from reader_sessions where id=? and user_id=? and book_id=?"
            + " and expires_at>now()", session, user.id(), book)
        .orElseThrow(() -> new ApiException(403, "Reader session expired. Reopen the book"));
    ReaderCrypto.verify(row.get("public_key").toString(),
        ReaderCrypto.proof(action, book, session, page, timestamp, nonce), signature);
    if (db.execute("insert into reader_request_nonces(session_id,nonce,expires_at)"
            + " values(?,?,now()+interval '60 seconds') on conflict do nothing", session, nonce) != 1)
      throw new ApiException(403, "Reader request was already used");
  }

  private void limit(String key, int count, long window) {
    if (!limits.consume("reader:" + key, count, window))
      throw new ApiException(429, "Please slow down and try again shortly");
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void cleanup() {
    db.execute("delete from reader_request_nonces where expires_at<now()");
    db.execute("delete from reader_sessions where expires_at<now()");
    db.execute("delete from reader_challenges where expires_at<now()");
  }
}
