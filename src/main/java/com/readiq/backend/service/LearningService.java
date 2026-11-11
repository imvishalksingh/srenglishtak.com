package com.readiq.backend.service;

import static com.readiq.backend.api.Input.*;

import com.readiq.backend.api.*;
import com.readiq.backend.data.Database;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
  private final Database db;
  private final CatalogService catalog;

  public LearningService(Database db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  public List<Map<String, Object>> progress(UUID user) {
    var rows =
        db.list("select * from user_progress where user_id=? order by last_read_at desc,id", user);
    for (var row : rows) row.put("books", safeBook(uuid(row.get("book_id"))));
    return rows;
  }

  @Transactional
  public Map<String, Object> updateProgress(UUID user, Map<String, Object> b) {
    UUID book = uuid(b.get("book_id"));
    catalog.book(book, false);
    int page = integer(b.getOrDefault("current_page", b.get("page")), "page", 0, 1_000_000);
    db.one("select id from profiles where id=? for update", user);
    var existing =
        db.optional("select * from user_progress where user_id=? and book_id=?", user, book);
    var v =
        map(
            "user_id",
            user,
            "book_id",
            book,
            "current_page",
            page,
            "last_read_at",
            OffsetDateTime.now(),
            "last_opened_at",
            OffsetDateTime.now());
    if (b.containsKey("progress_percent")) {
      if (!(b.get("progress_percent") instanceof Number n)
          || !Double.isFinite(n.doubleValue())
          || n.doubleValue() < 0
          || n.doubleValue() > 100) throw ApiException.bad("Invalid progress_percent");
      v.put("progress_percent", n);
    }
    if (b.containsKey("is_completed")) {
      boolean complete = bool(b.get("is_completed"), "is_completed");
      v.put("is_completed", complete);
      if (complete && existing.map(e -> e.get("completed_at") == null).orElse(true))
        v.put("completed_at", OffsetDateTime.now());
    }
    if (b.containsKey("total_minutes_read")) {
      int delta = integer(b.get("total_minutes_read"), "total_minutes_read", 0, 1440);
      int old = existing.map(e -> ((Number) e.get("total_minutes_read")).intValue()).orElse(0);
      if (old > Integer.MAX_VALUE - delta) throw ApiException.bad("Reading total is too large");
      v.put("total_minutes_read", old + delta);
    }
    if (b.containsKey("last_position"))
      v.put("last_position", text(b, "last_position", 10000, false));
    if (existing.isEmpty()) return db.insert("user_progress", v);
    v.remove("user_id");
    v.remove("book_id");
    return db.update("user_progress", uuid(existing.get().get("id")), v);
  }

  public List<Map<String, Object>> ownedList(String table, UUID user, UUID book) {
    String order =
        switch (table) {
          case "reading_sessions" -> "started_at";
          case "bookmarks" -> "created_at";
          case "notes" -> "updated_at";
          default -> throw new IllegalArgumentException();
        };
    return db.list(
        "select * from "
            + table
            + " where user_id=?"
            + (book == null ? "" : " and book_id=?")
            + " order by "
            + order
            + " desc,id",
        book == null ? new Object[] {user} : new Object[] {user, book});
  }

  @Transactional
  public Map<String, Object> startSession(UUID user, Map<String, Object> b) {
    UUID book = uuid(b.get("book_id"));
    catalog.book(book, false);
    int page =
        b.get("start_page") == null ? 0 : integer(b.get("start_page"), "start_page", 0, 1_000_000);
    return db.insert(
        "reading_sessions",
        map(
            "user_id",
            user,
            "book_id",
            book,
            "start_page",
            page,
            "end_page",
            page,
            "device_type",
            text(b, "device_type", 100, false),
            "source",
            text(b, "source", 100, false)));
  }

  @Transactional
  public Map<String, Object> finishSession(UUID user, UUID id, Map<String, Object> b) {
    db.one("select id from reading_sessions where id=? and user_id=? for update", id, user);
    var v = map("ended_at", OffsetDateTime.now());
    for (String field : List.of("end_page", "pages_read", "minutes_spent"))
      if (b.containsKey(field))
        v.put(
            field,
            integer(b.get(field), field, 0, field.equals("minutes_spent") ? 1440 : 1_000_000));
    if (b.containsKey("is_completed"))
      v.put("is_completed", bool(b.get("is_completed"), "is_completed"));
    return db.update("reading_sessions", id, v);
  }

  public Map<String, Object> createAnnotation(String table, UUID user, Map<String, Object> b) {
    if (!Set.of("notes", "bookmarks").contains(table)) throw new IllegalArgumentException();
    UUID book = uuid(b.get("book_id"));
    catalog.book(book, false);
    var v = fields(b, "page", "position");
    v.put("user_id", user);
    v.put("book_id", book);
    if (table.equals("notes")) v.put("note_text", text(b, "note_text", 50000, true).strip());
    else v.put("label", text(b, "label", 500, false));
    return db.insert(table, v);
  }

  @Transactional
  public void deleteAnnotation(String table, UUID user, UUID id) {
    if (!Set.of("notes", "bookmarks").contains(table)) throw new IllegalArgumentException();
    if (db.execute("delete from " + table + " where id=? and user_id=?", id, user) == 0)
      throw ApiException.missing();
  }

  public Map<String, Object> profile(UUID user) {
    return profileResult(db.byId("profiles", user));
  }

  private Map<String,Object> profileResult(Map<String,Object> row) {
    var result = new LinkedHashMap<>(row);
    result.put("onboarding_completed", row.get("onboarding_completed_at") != null
        && Objects.toString(row.get("phone_number"), "").matches("\\+[1-9][0-9]{7,14}"));
    return result;
  }

  @Transactional
  public Map<String,Object> completeOnboarding(UUID user, Map<String,Object> body) {
    String name=text(body,"full_name",100,true).strip();
    String phone=PhoneNumber.normalize(body.get("phone_number"));
    String studentClass=text(body,"class",120,true).strip();
    String goal=text(body,"learning_goal",500,true).strip();
    return profileResult(db.one("update profiles set full_name=?,phone_number=?,class=?,learning_goal=?,"
        + "onboarding_completed_at=coalesce(onboarding_completed_at,now()) where id=? returning *",
        name,phone,studentClass,goal,user));
  }

  public Map<String, Object> updateProfile(UUID user, Map<String, Object> b) {
    var v = fields(b, "full_name", "avatar_url", "class", "learning_goal", "phone_number");
    if (v.containsKey("phone_number")) v.put("phone_number",PhoneNumber.normalize(v.get("phone_number")));
    for (String f : List.of("full_name", "class", "learning_goal", "avatar_url"))
      if (v.get(f) != null && v.get(f).toString().length() > (f.equals("avatar_url") ? 2000 : 500))
        throw ApiException.bad("Invalid " + f);
    return profileResult(db.update("profiles", user, v));
  }

  public Object grammarOverview(UUID user) {
    return map(
        "units", db.list("select * from grammar_units order by unit_order,id"),
        "lessons", db.list("select id,unit_id,title,content_type,lesson_order,created_at from grammar_lessons order by lesson_order,id"),
        "progress", grammarProgress(user));
  }

  public Object grammarProgress(UUID user) {
    return db.list(
        "select * from grammar_progress where user_id=? order by completed_at desc,id", user);
  }

  @Transactional
  public Object completeLesson(UUID user, UUID lesson) {
    db.byId("grammar_lessons", lesson);
    db.execute(
        "insert into grammar_progress(user_id,lesson_id,is_completed,completed_at)"
            + " values(?,?,true,now()) on conflict(user_id,lesson_id) do update set"
            + " is_completed=true,completed_at=coalesce(grammar_progress.completed_at,excluded.completed_at)",
        user,
        lesson);
    return db.one("select * from grammar_progress where user_id=? and lesson_id=?", user, lesson);
  }

  public Map<String, Object> interest(UUID user, String title) {
    if (title.isBlank() || title.length() > 200) throw ApiException.bad("Invalid moduleTitle");
    var row =
        db.optional(
                "select * from module_interests where user_id=? and module_title=?", user, title)
            .orElse(Map.of());
    return map(
        "excitedCount",
        db.count(
            "select count(*) from module_interests where module_title=? and is_excited=true",
            title),
        "isNotified",
        row.getOrDefault("is_notified", false),
        "isExcited",
        row.getOrDefault("is_excited", false));
  }

  @Transactional
  public Map<String, Object> updateInterest(UUID user, Map<String, Object> b) {
    String title = text(b, "moduleTitle", 200, true);
    Boolean notified = b.containsKey("isNotified") ? bool(b.get("isNotified"), "isNotified") : null;
    Boolean excited = b.containsKey("isExcited") ? bool(b.get("isExcited"), "isExcited") : null;
    db.execute(
        "insert into module_interests(user_id,module_title,is_notified,is_excited)"
            + " values(?,?,coalesce(?,false),coalesce(?,false)) on conflict(user_id,module_title)"
            + " do update set"
            + " is_notified=coalesce(?,module_interests.is_notified),is_excited=coalesce(?,module_interests.is_excited)",
        user,
        title,
        notified,
        excited,
        notified,
        excited);
    return interest(user, title);
  }

  private Map<String, Object> safeBook(UUID id) {
    var b = db.byId("books", id);
    b.put("file_url", null);
    return b;
  }
}
