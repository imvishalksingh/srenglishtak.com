package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.data.Database;
import com.readiq.backend.security.DatabaseRateLimitStore;
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
  private final ReaderBookAccess access;
  private final BookPageRenderer renderer;
  private final PlayIntegrityVerifier integrity;
  private final ReaderSettings settings;
  private final TransactionTemplate tx;

  public record Challenge(UUID challenge_id, String request_hash, long cloud_project_number, boolean initial_page_supported) {}
  public record Session(UUID session_id, int page_count, long expires_at, Integer initial_page, byte[] initial_image) {}

  public ProtectedReaderService(Database db, DatabaseRateLimitStore limits, ReaderBookAccess access,
      BookPageRenderer renderer, PlayIntegrityVerifier integrity,
      ReaderSettings settings, PlatformTransactionManager manager) {
    this.db = db; this.limits = limits; this.access = access;
    this.renderer = renderer; this.integrity = integrity; this.settings = settings;
    this.tx = new TransactionTemplate(manager);
  }

  public Challenge challenge(ApiPrincipal user, UUID book, String key) {
    access.load(user,book,null);
    integrity.requireConfigured();
    ReaderCrypto.publicKey(key);
    limit("challenge:" + user.id(), 6, 60000);
    UUID id = UUID.randomUUID();
    String hash = ReaderCrypto.hash("reader-v1\n" + user.id() + "\n" + book + "\n" + id + "\n" + key);
    db.execute("insert into reader_challenges(id,user_id,book_id,public_key,request_hash,expires_at)"
        + " values(?,?,?,?,?,now()+interval '90 seconds')", id, user.id(), book, key, hash);
    return new Challenge(id, hash, settings.cloudProjectNumber(), true);
  }

  public Session activate(ApiPrincipal user, UUID book, UUID challenge, String token, String signature, Integer initialPage) {
    return activate(user,book,challenge,token,signature,initialPage,ignored -> {});
  }
  public Session activate(ApiPrincipal user, UUID book, UUID challenge, String token, String signature,
      Integer initialPage, java.util.function.Consumer<String> timing) {
    var row = access.challenge(user,book,challenge);
    String key = row.publicKey(), hash = row.requestHash();
    ReaderCrypto.verify(key, "activate\n" + challenge + "\n" + hash
        + (initialPage==null ? "" : "\npage\n"+initialPage), signature);
    limit("activate:" + user.id(), 6, 60000);
    // Consume before the external call so a replay cannot spend verification/render capacity.
    if (db.execute("delete from reader_challenges where id=? and user_id=? and expires_at>now()",
        challenge, user.id()) != 1) throw new ApiException(403, "Reader challenge was already used");
    timing.accept("authorize");
    integrity.verify(token, hash); timing.accept("integrity");
    var verifiedBook = access.load(user,book,null); timing.accept("entitlement");
    int count = renderer.pageCount(verifiedBook); timing.accept("document");
    UUID id = UUID.randomUUID();
    long expiry = Instant.now().plusSeconds(300).getEpochSecond();
    tx.executeWithoutResult(status -> {
      // Account lock coordinates concurrent session creation across replicas.
      db.one("select id from profiles where id=? for update", user.id());
      // Keep the lock in the previous statement: READ COMMITTED needs a fresh
      // snapshot after a competing session creator commits.
      db.execute("with previous as (delete from reader_sessions where user_id=? returning id)"
          + " insert into reader_sessions(id,user_id,book_id,public_key,expires_at)"
          + " select ?,?,?,?,to_timestamp(?) from (select count(*) from previous) removed", user.id(), id, user.id(), book, key, expiry);
    });
    timing.accept("session");
    Integer requested=initialPage==null ? null : Math.min(initialPage,count);
    if (requested!=null) limit("page:"+user.id(),30,60000);
    byte[] image=requested==null ? null : renderer.render(verifiedBook,requested,user.id(),id);
    timing.accept("render");
    return new Session(id, count, expiry, requested, image);
  }

  public byte[] page(ApiPrincipal user, UUID book, UUID session, int page,
      long timestamp, UUID nonce, String signature) {
    return page(user,book,session,page,timestamp,nonce,signature,ignored -> {});
  }
  public byte[] page(ApiPrincipal user, UUID book, UUID session, int page,
      long timestamp, UUID nonce, String signature, java.util.function.Consumer<String> timing) {
    if (page < 1 || page > 2000) throw ApiException.missing();
    var verifiedBook=authorize(user, book, session, "page", page, timestamp, nonce, signature);
    timing.accept("authorize");
    byte[] image=renderer.render(verifiedBook,page,user.id(),session);
    timing.accept("render"); return image;
  }

  public void close(ApiPrincipal user, UUID book, UUID session, long timestamp, UUID nonce, String signature) {
    authorize(user, book, session, "close", 0, timestamp, nonce, signature);
    db.execute("delete from reader_sessions where id=? and user_id=?", session, user.id());
  }

  private ReaderBookAccess.AuthorizedBook authorize(ApiPrincipal user, UUID book, UUID session, String action, int page,
      long timestamp, UUID nonce, String signature) {
    var verifiedBook=access.load(user,book,session);
    long now = Instant.now().getEpochSecond();
    if (nonce == null || timestamp < now - 30 || timestamp > now + 10)
      throw new ApiException(403, "Reader request expired");
    ReaderCrypto.verify(verifiedBook.publicKey(),
        ReaderCrypto.proof(action, book, session, page, timestamp, nonce), signature);
    if (action.equals("page")) {
      String result=limits.consumePage(session,nonce,"reader:page:"+user.id(),30,60000);
      if ("replay".equals(result)) throw new ApiException(403,"Reader request was already used");
      if (!"allowed".equals(result)) throw new ApiException(429,"Please slow down and try again shortly");
    } else if (db.execute("insert into reader_request_nonces(session_id,nonce,expires_at)"
            + " values(?,?,now()+interval '60 seconds') on conflict do nothing", session, nonce) != 1)
      throw new ApiException(403, "Reader request was already used");
    return verifiedBook;
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
