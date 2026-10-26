package com.readiq.backend.api;

import com.readiq.backend.service.ReportingService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
  private final ReportingService service;
  private final com.readiq.backend.data.Database db;

  public AdminController(ReportingService service, com.readiq.backend.data.Database db) {
    this.service = service;
    this.db = db;
  }

  @GetMapping("/purchases")
  public Object purchases(@RequestParam(defaultValue = "0") int offset) {
    if (offset < 0 || offset > 1000000) throw ApiException.bad("Invalid offset");
    return db.list(
        "select"
            + " p.id,p.user_id,u.email,p.book_id,b.title,p.provider,p.environment,p.status,p.amount,p.currency,p.created_at,p.checked_at"
            + " from book_purchases p join profiles u on u.id=p.user_id join books b on"
            + " b.id=p.book_id order by p.created_at desc,p.id limit 50 offset ?",
        offset);
  }

  @GetMapping("/stats")
  public Object stats() {
    return service.stats();
  }

  @GetMapping("/users")
  public Object users() {
    return service.users();
  }

  @GetMapping("/users/{userId}/progress")
  public Object progress(@PathVariable UUID userId) {
    return service.userProgress(userId);
  }
}
