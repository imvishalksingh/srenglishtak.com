package com.readiq.backend.payment;

import static com.readiq.backend.api.Input.*;

import com.readiq.backend.api.*;
import com.readiq.backend.auth.AccessTokenVerifier;
import com.readiq.backend.data.*;
import com.readiq.backend.data.entity.BooksEntity;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

@Service
public class PurchaseService {
  private final Database db;
  private final BookRepository books;
  private final PaymentSettings settings;
  private final GooglePlayClient google;
  private final RazorpayClient razorpay;
  private final PaymentCrypto crypto;
  private final TransactionTemplate tx;
  private final ObjectMapper json;

  public PurchaseService(
      Database db,
      BookRepository books,
      PaymentSettings settings,
      GooglePlayClient google,
      RazorpayClient razorpay,
      PaymentCrypto crypto,
      PlatformTransactionManager manager,
      ObjectMapper json) {
    this.db = db;
    this.books = books;
    this.settings = settings;
    this.google = google;
    this.razorpay = razorpay;
    this.crypto = crypto;
    this.tx = new TransactionTemplate(manager);
    this.json = json;
  }

  public record BillingContext(
      String environment,
      boolean android_enabled,
      boolean website_enabled,
      String android_account_id) {}

  public record Access(
      UUID book_id,
      boolean has_access,
      boolean requires_purchase,
      int price_paise,
      String currency) {}

  public record Checkout(
      UUID id,
      String provider_order_id,
      String key_id,
      int amount,
      String currency,
      String environment) {}

  public BillingContext context(UUID user) {
    return new BillingContext(
        settings.getMode(), settings.googleEnabled(), settings.razorpayEnabled(), account(user));
  }

  public static String account(UUID user) {
    return AccessTokenVerifier.hash("readiq:" + user);
  }

  public Access access(UUID user, UUID book, boolean admin, boolean refresh) {
    BooksEntity b = book(book, true);
    boolean paid = b.price_paise != null && b.price_paise > 0;
    boolean allowed = admin || !paid;
    if (!allowed && user != null) {
      var rows =
          db.list(
              "select * from book_purchases where user_id=? and book_id=? and environment=? and"
                  + " status='active'",
              user,
              book,
              settings.getMode());
      if (refresh)
        for (var row : rows) {
          if (Instant.parse(row.get("checked_at").toString())
              .isBefore(Instant.now().minusSeconds(300))) recheck(row);
        }
      allowed =
          db.count(
                  "select count(*) from book_purchases where user_id=? and book_id=? and"
                      + " environment=? and status='active'",
                  user,
                  book,
                  settings.getMode())
              > 0;
    }
    if (!Boolean.TRUE.equals(b.is_visible) && !admin && !(paid && allowed))
      throw ApiException.missing();
    return new Access(book, allowed, paid, b.price_paise == null ? 0 : b.price_paise, "INR");
  }

  public void requireAccess(UUID user, UUID book, boolean admin) {
    if (!access(user, book, admin, true).has_access())
      throw new ApiException(402, "Purchase this book to read it");
  }

  public List<com.readiq.backend.dto.BookResponse> purchasedBooks(UUID user) {
    var rows =
        db.list(
            "select b.* from books b where exists(select 1 from book_purchases p where"
                + " p.book_id=b.id and p.user_id=? and p.environment=? and p.status='active') order"
                + " by b.created_at desc,b.id",
            user,
            settings.getMode());
    rows.forEach(b -> b.put("file_url", null));
    return rows.stream().map(com.readiq.backend.dto.BookResponse::from).toList();
  }

  public List<Map<String, Object>> purchases(UUID user) {
    return db.list(
        "select id,book_id,provider,environment,status,created_at from book_purchases where"
            + " user_id=? and environment=? order by created_at desc",
        user,
        settings.getMode());
  }

  public Map<String, Object> verifyGoogle(UUID user, UUID book, String token) {
    settings.requireGoogle();
    if (token == null || token.length() < 10 || token.length() > 8192)
      throw ApiException.bad("Invalid purchase token");
    JsonNode result = google.purchase(token);
    if (book == null) {
      String product = result.path("productLineItem").path(0).path("productId").asText();
      book =
          books
              .byAndroidProduct(product)
              .orElseThrow(() -> new ApiException(422, "Unknown book product"))
              .id;
    }
    BooksEntity b = book(book, true);
    if (b.android_product_id == null || b.price_paise <= 0)
      throw ApiException.bad("This book is not configured for Google Play");
    validateGoogle(result, user, b.android_product_id);
    String hash = AccessTokenVerifier.hash(token);
    String encrypted = crypto.encrypt(token);
    if (!"ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(result.path("acknowledgementState").asText()))
      google.acknowledge(b.android_product_id, token);
    grant(
        user,
        book,
        "google_play",
        hash,
        result.path("orderId").asText(),
        b.android_product_id,
        encrypted,
        null,
        null);
    return map("book_id", book, "has_access", true, "status", "verified");
  }

  void validateGoogle(JsonNode result, UUID user, String product) {
    if (!"PURCHASED".equals(result.path("purchaseStateContext").path("purchaseState").asText()))
      throw new ApiException(422, "Payment is pending, canceled, or refunded");
    if (!account(user).equals(result.path("obfuscatedExternalAccountId").asText()))
      throw new ApiException(403, "Purchase belongs to a different app account");
    if (result.path("productLineItem").size() != 1)
      throw new ApiException(422, "Invalid purchased product");
    JsonNode item = result.path("productLineItem").get(0), offer = item.path("productOfferDetails");
    if (!product.equals(item.path("productId").asText())
        || offer.path("quantity").asInt(0) != 1
        || offer.path("refundableQuantity").asInt(0) != 1
        || offer.has("rentOfferDetails")
        || offer.has("preorderOfferDetails")
        || "CONSUMPTION_STATE_CONSUMED".equals(offer.path("consumptionState").asText()))
      throw new ApiException(422, "Purchase does not match this non-consumable book");
    boolean test = result.has("testPurchaseContext");
    if (test != settings.getMode().equals("test"))
      throw new ApiException(422, "Purchase environment mismatch");
  }

  public Checkout createOrder(UUID user, UUID book, UUID requestKey) {
    settings.requireRazorpay();
    var local =
        tx.execute(
            status -> {
              // Serialize a user's checkout requests across replicas. Price comes from the locked
              // database row.
              db.one("select id from profiles where id=? for update", user);
              var old =
                  db.optional(
                      "select * from purchase_orders where user_id=? and request_key=?",
                      user,
                      requestKey);
              if (old.isPresent()) {
                if (!book.equals(uuid(old.get().get("book_id")))
                    || !settings.getMode().equals(old.get().get("environment")))
                  throw new ApiException(409, "Checkout request key already used");
                return old.get();
              }
              var b = db.one("select * from books where id=? and is_visible=true for share", book);
              int amount = ((Number) b.get("price_paise")).intValue();
              if (amount <= 0) throw ApiException.bad("This book is free");
              if (access(user, book, false, false).has_access())
                throw new ApiException(409, "You already own this book");
              UUID id = UUID.randomUUID();
              return db.one(
                  "insert into"
                      + " purchase_orders(id,user_id,book_id,request_key,environment,amount,currency)"
                      + " values(?,?,?,?,?,?,'INR') returning *",
                  id,
                  user,
                  book,
                  requestKey,
                  settings.getMode(),
                  amount);
            });
    if (local.get("provider_order_id") == null) {
      // Persist provider creation under a row lock; retries reuse the existing provider order.
      local =
          tx.execute(
              status -> {
                var locked =
                    db.one(
                        "select * from purchase_orders where id=? for update",
                        uuid(localId(user, requestKey)));
                if (locked.get("provider_order_id") == null) {
                  var provider =
                      razorpay.createOrder(
                          uuid(locked.get("id")), ((Number) locked.get("amount")).intValue());
                  if (provider.path("id").asText().isBlank()
                      || provider.path("amount").asInt()
                          != ((Number) locked.get("amount")).intValue()
                      || !"INR".equals(provider.path("currency").asText()))
                    throw new ApiException(502, "Invalid provider order response");
                  return db.one(
                      "update purchase_orders set provider_order_id=? where id=? returning *",
                      provider.path("id").asText(),
                      uuid(locked.get("id")));
                }
                return locked;
              });
    }
    return new Checkout(
        uuid(local.get("id")),
        local.get("provider_order_id").toString(),
        settings.getRazorpayKeyId(),
        ((Number) local.get("amount")).intValue(),
        "INR",
        settings.getMode());
  }

  private Object localId(UUID user, UUID key) {
    return db.one("select id from purchase_orders where user_id=? and request_key=?", user, key)
        .get("id");
  }

  public Map<String, Object> verifyRazorpay(
      UUID user, UUID id, String paymentId, String signature) {
    settings.requireRazorpay();
    var order =
        db.one(
            "select * from purchase_orders where id=? and user_id=? and environment=?",
            id,
            user,
            settings.getMode());
    String providerId = Objects.toString(order.get("provider_order_id"), "");
    if (!PaymentCrypto.signature(
        (providerId + "|" + paymentId).getBytes(StandardCharsets.UTF_8),
        settings.getRazorpayKeySecret(),
        signature)) throw new ApiException(400, "Invalid payment signature");
    capture(order, paymentId);
    return map("book_id", order.get("book_id"), "has_access", true, "status", "verified");
  }

  private void capture(Map<String, Object> order, String paymentId) {
    JsonNode payment = razorpay.payment(paymentId),
        providerOrder = razorpay.order(order.get("provider_order_id").toString());
    int amount = ((Number) order.get("amount")).intValue();
    if (!settings.getMode().equals(order.get("environment"))
        || !"captured".equals(payment.path("status").asText())
        || !payment.path("captured").asBoolean()
        || !paymentId.equals(payment.path("id").asText())
        || !order.get("provider_order_id").equals(payment.path("order_id").asText())
        || payment.path("amount").asInt() != amount
        || payment.path("amount_refunded").asInt() != 0
        || !"INR".equals(payment.path("currency").asText())
        || !order.get("id").toString().equals(providerOrder.path("receipt").asText())
        || providerOrder.path("amount").asInt() != amount
        || !"paid".equals(providerOrder.path("status").asText())
        || providerOrder.path("amount_paid").asInt() != amount
        || !"INR".equals(providerOrder.path("currency").asText()))
      throw new ApiException(
          422, "Payment has not been captured for the expected order and amount");
    grant(
        uuid(order.get("user_id")),
        uuid(order.get("book_id")),
        "razorpay",
        AccessTokenVerifier.hash(paymentId),
        paymentId,
        null,
        null,
        amount,
        "INR");
    db.execute(
        "update purchase_orders set status='paid' where id=? and status<>'revoked'",
        uuid(order.get("id")));
  }

  private void grant(
      UUID user,
      UUID book,
      String provider,
      String hash,
      String paymentId,
      String product,
      String token,
      Integer amount,
      String currency) {
    tx.executeWithoutResult(
        status -> {
          lock(provider, hash);
          var currentBook = db.one("select * from books where id=? for share", book);
          if (provider.equals("google_play")
              && !Objects.equals(product, currentBook.get("android_product_id")))
            throw new ApiException(409, "Product mapping changed; retry restoring the purchase");
          if (db.count(
                  "select count(*) from purchase_revocations where provider=? and reference_hash=?",
                  provider,
                  hash)
              > 0) throw new ApiException(422, "Purchase was revoked");
          var old =
              db.optional(
                  "select * from book_purchases where provider=? and reference_hash=?",
                  provider,
                  hash);
          if (old.isPresent()) {
            if (!user.equals(uuid(old.get().get("user_id")))
                || !book.equals(uuid(old.get().get("book_id"))))
              throw new ApiException(409, "Purchase already linked to another account or book");
            if (!"active".equals(old.get().get("status")))
              throw new ApiException(422, "Purchase was revoked");
            db.execute(
                "update book_purchases set checked_at=now() where id=?", uuid(old.get().get("id")));
            return;
          }
          db.execute(
              "insert into"
                  + " book_purchases(user_id,book_id,provider,environment,reference_hash,provider_payment_id,product_id,encrypted_token,amount,currency,status)"
                  + " values(?,?,?,?,?,?,?,?,?,?,'active')",
              user,
              book,
              provider,
              settings.getMode(),
              hash,
              paymentId,
              product,
              token,
              amount,
              currency);
        });
  }

  private void lock(String provider, String hash) {
    db.list("select pg_advisory_xact_lock(hashtextextended(?,0))", provider + ":" + hash);
  }

  public void revoke(String provider, String hash) {
    tx.executeWithoutResult(
        status -> {
          lock(provider, hash);
          db.execute(
              "insert into purchase_revocations(provider,reference_hash) values(?,?) on conflict do"
                  + " nothing",
              provider,
              hash);
          db.execute(
              "update book_purchases set status='revoked',checked_at=now() where provider=? and"
                  + " reference_hash=?",
              provider,
              hash);
        });
  }

  public void webhook(byte[] body, String signature) {
    settings.requireRazorpay();
    if (!PaymentCrypto.signature(body, settings.getRazorpayWebhookSecret(), signature))
      throw new ApiException(400, "Invalid webhook signature");
    try {
      JsonNode event = json.readTree(body);
      String type = event.path("event").asText();
      if (Set.of("refund.processed", "payment.dispute.created").contains(type)) {
        String kind = type.startsWith("refund") ? "refund" : "dispute";
        String payment =
            event.path("payload").path(kind).path("entity").path("payment_id").asText();
        if (payment.isBlank()) throw ApiException.bad("Invalid webhook event");
        revoke("razorpay", AccessTokenVerifier.hash(payment));
      } else if (Set.of("payment.captured", "order.paid").contains(type)) {
        JsonNode payment = event.path("payload").path("payment").path("entity");
        var order =
            db.optional(
                "select * from purchase_orders where provider_order_id=? and environment=?",
                payment.path("order_id").asText(),
                settings.getMode());
        if (order.isPresent()
            && db.count(
                    "select count(*) from purchase_revocations where provider='razorpay' and"
                        + " reference_hash=?",
                    AccessTokenVerifier.hash(payment.path("id").asText()))
                == 0) capture(order.get(), payment.path("id").asText());
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw ApiException.bad("Invalid webhook body");
    }
  }

  public void recheck(Map<String, Object> row) {
    if (!settings.getMode().equals(row.get("environment"))) return;
    String provider = row.get("provider").toString(), hash = row.get("reference_hash").toString();
    try {
      if (provider.equals("google_play")) {
        var result = google.purchase(crypto.decrypt(row.get("encrypted_token").toString()));
        validateGoogle(result, uuid(row.get("user_id")), row.get("product_id").toString());
      } else {
        JsonNode payment = razorpay.payment(row.get("provider_payment_id").toString());
        if (!"captured".equals(payment.path("status").asText())
            || payment.path("amount_refunded").asInt() != 0)
          throw new ApiException(422, "Purchase refunded");
      }
      db.execute("update book_purchases set checked_at=now() where id=?", uuid(row.get("id")));
    } catch (ApiException ex) {
      if (Set.of(403, 404, 410, 422).contains(ex.status())) revoke(provider, hash);
      else throw ex;
    }
  }

  private BooksEntity book(UUID id, boolean admin) {
    BooksEntity b = books.findById(id).orElseThrow(ApiException::missing);
    if (!admin && !Boolean.TRUE.equals(b.is_visible)) throw ApiException.missing();
    return b;
  }
}
