package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.data.Database;
import com.readiq.backend.payment.PaymentSettings;
import com.readiq.backend.payment.PurchaseService;
import java.util.*;
import org.springframework.stereotype.Service;

/** Fresh authorization/storage/session state in one DB round trip. No entitlement cache. */
@Service
public class ReaderBookAccess {
  record AuthorizedBook(Map<String,Object> metadata, String publicKey) {}
  private final Database db;
  private final PaymentSettings settings;
  private final PurchaseService purchases;
  public ReaderBookAccess(Database db, PaymentSettings settings, PurchaseService purchases) {
    this.db=db; this.settings=settings; this.purchases=purchases;
  }
  AuthorizedBook load(ApiPrincipal user, UUID book, UUID session) {
    var row=query(user,book,session);
    if (!user.admin() && Boolean.TRUE.equals(row.get("reader_stale"))) {
      // Keep the existing Google receipt/refund recheck interval and failure behavior.
      purchases.requireAccess(user.id(),book,false);
      row=query(user,book,session);
    }
    boolean paid=row.get("price_paise") instanceof Number price && price.intValue()>0;
    boolean allowed=user.admin() || !paid || Boolean.TRUE.equals(row.get("reader_owned"));
    if (!Boolean.TRUE.equals(row.get("is_visible")) && !user.admin() && !(paid && allowed)) throw ApiException.missing();
    if (!allowed) throw new ApiException(402,"Purchase this book to read it");
    if (session!=null && row.get("reader_public_key")==null)
      throw new ApiException(403,"Reader session expired. Reopen the book");
    if (!Boolean.TRUE.equals(row.get("reader_private")))
      throw new ApiException(503,"Private book storage is not configured");
    return new AuthorizedBook(row, Objects.toString(row.get("reader_public_key"),null));
  }
  private Map<String,Object> query(ApiPrincipal user, UUID book, UUID session) {
    return db.one("""
        select b.*, coalesce(p.owned,false) as reader_owned, coalesce(p.stale,false) as reader_stale,
          exists(select 1 from storage.buckets where id='book-files' and public=false) as reader_private,
          s.public_key as reader_public_key
        from books b
        left join lateral (
          select count(*)>0 as owned,
            bool_or(coalesce(checked_at<now()-interval '300 seconds',true)) as stale
          from book_purchases where user_id=? and book_id=b.id and environment=? and status='active'
        ) p on true
        left join reader_sessions s on s.id=? and s.user_id=? and s.book_id=b.id and s.expires_at>now()
        where b.id=?
        """, user.id(),settings.getMode(),session,user.id(),book);
  }
}
