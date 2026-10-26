package com.readiq.backend.payment;

import com.readiq.backend.data.Database;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchaseReconciler {
  private static final Logger log = LoggerFactory.getLogger(PurchaseReconciler.class);
  private final Database db;
  private final PurchaseService service;
  private final PaymentSettings settings;

  public PurchaseReconciler(Database db, PurchaseService service, PaymentSettings settings) {
    this.db = db;
    this.service = service;
    this.settings = settings;
  }

  @Scheduled(
      initialDelayString = "${payments.reconcile-delay-ms:60000}",
      fixedDelayString = "${payments.reconcile-delay-ms:60000}")
  public void reconcile() {
    if (settings.getMode().equals("disabled")) return;
    for (var row :
        db.list(
            "select * from book_purchases where status='active' and environment=? and"
                + " checked_at<now()-interval '5 minutes' order by checked_at limit 100",
            settings.getMode())) {
      try {
        service.recheck(row);
      } catch (Exception ex) {
        log.warn("Purchase reconciliation deferred: {}", ex.getClass().getSimpleName());
      }
    }
  }
}
