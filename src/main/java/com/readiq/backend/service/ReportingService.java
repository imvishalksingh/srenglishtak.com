package com.readiq.backend.service;

import static com.readiq.backend.api.Input.*;

import com.readiq.backend.data.Database;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingService {
  private final Database db;
  private final LearningService learning;
  private final ZoneId zone;

  public ReportingService(
      Database db,
      LearningService learning,
      @Value("${app.timezone:Asia/Kolkata}") String timezone) {
    this.db = db;
    this.learning = learning;
    this.zone = ZoneId.of(timezone);
  }

  @Transactional
  public Map<String, Object> insights(UUID user) {
    var sessions = learning.ownedList("reading_sessions", user, null);
    var dates = new TreeSet<LocalDate>();
    long minutes = 0;
    LocalDate today = LocalDate.now(zone);
    for (var s : sessions) {
      LocalDate day = AssessmentService.instant(s.get("started_at")).atZone(zone).toLocalDate();
      dates.add(day);
      if (day.equals(today)) minutes += ((Number) s.get("minutes_spent")).longValue();
    }
    int longest = 0, running = 0;
    LocalDate previous = null;
    for (LocalDate day : dates) {
      running = previous != null && previous.plusDays(1).equals(day) ? running + 1 : 1;
      longest = Math.max(longest, running);
      previous = day;
    }
    LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
    int current = 0;
    while (dates.contains(cursor)) {
      current++;
      cursor = cursor.minusDays(1);
    }
    long complete =
        db.count("select count(*) from user_progress where user_id=? and is_completed=true", user);
    long attempts = db.count("select count(*) from quiz_attempts where user_id=?", user);
    var result =
        map(
            "current_streak",
            current,
            "longest_streak",
            longest,
            "reading_minutes_today",
            minutes,
            "active_days",
            dates.size(),
            "completed_books",
            complete,
            "quiz_attempts",
            attempts,
            "total_sessions",
            sessions.size());
    if (sessions.stream().anyMatch(s -> s.get("ended_at") != null))
      unlock(user, "first_session", "First Session", "Completed your first reading session.");
    if (attempts > 0)
      unlock(user, "quiz_starter", "Quiz Starter", "Completed your first quiz attempt.");
    if (complete > 0) unlock(user, "book_finisher", "Book Finisher", "Completed your first book.");
    if (longest >= 3)
      unlock(user, "streak_3", "3 Day Streak", "Stayed active for 3 days in a row.");
    if (longest >= 7)
      unlock(user, "streak_7", "7 Day Streak", "Stayed active for 7 days in a row.");
    result.put("weekly_goal", weeklyGoal(user));
    result.put(
        "achievements_unlocked",
        db.count("select count(*) from user_achievements where user_id=?", user));
    return result;
  }

  public Map<String, Object> weeklyGoal(UUID user) {
    Instant now = Instant.now();
    Instant from = WeeklyLearningGoal.weekStart(now, zone).atStartOfDay(zone).toInstant();
    var events = db.list(
        "select occurred_at from ("
            + "select started_at as occurred_at from reading_sessions where user_id=? and (minutes_spent>0 or pages_read>0)"
            + " union all select completed_at from grammar_progress where user_id=? and is_completed=true"
            + " union all select completed_at from quiz_attempts where user_id=?"
            + " union all select completed_at from challenge_results where user_id=?"
            + ") activity where occurred_at>=? and occurred_at<=?",
        user, user, user, user, from.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
    var instants = events.stream().map(row -> AssessmentService.instant(row.get("occurred_at"))).toList();
    return WeeklyLearningGoal.summarize(now, zone, instants);
  }

  private void unlock(UUID user, String code, String title, String description) {
    db.execute(
        "insert into user_achievements(user_id,code,title,description) values(?,?,?,?) on"
            + " conflict(user_id,code) do nothing",
        user,
        code,
        title,
        description);
  }

  @Transactional
  public Object achievements(UUID user) {
    return map(
        "summary",
        insights(user),
        "achievements",
        db.list(
            "select * from user_achievements where user_id=? order by unlocked_at desc,id", user));
  }

  public Object recommendations(UUID user) {
    var progress = learning.progress(user);
    var books = db.list("select * from books where is_visible=true order by created_at desc,id");
    var quizzes =
        db.list("select * from quizzes where is_visible=true order by created_at desc,id");
    var taken = new HashSet<String>();
    for (var a : db.list("select quiz_id from quiz_attempts where user_id=?", user))
      taken.add(a.get("quiz_id").toString());
    var started = new HashSet<String>();
    for (var p : progress) started.add(p.get("book_id").toString());
    var result = new ArrayList<Map<String, Object>>();
    var ongoing =
        progress.stream().filter(p -> !Boolean.TRUE.equals(p.get("is_completed"))).findFirst();
    if (ongoing.isPresent()) {
      var p = ongoing.get();
      books.stream()
          .filter(b -> b.get("id").equals(p.get("book_id")))
          .findFirst()
          .ifPresent(
              b ->
                  result.add(
                      bookRecommendation(
                          b,
                          "continue_book",
                          "Continue " + b.get("title"),
                          "Pick up from page " + p.get("current_page"),
                          "Resume")));
    }
    books.stream()
        .filter(b -> !started.contains(b.get("id").toString()))
        .findFirst()
        .ifPresent(
            b ->
                result.add(
                    bookRecommendation(
                        b,
                        "new_book",
                        "Start " + b.get("title"),
                        Objects.toString(b.get("author"), "Try something new today"),
                        "Start Reading")));
    quizzes.stream()
        .filter(q -> "general".equals(q.get("type")) && !taken.contains(q.get("id").toString()))
        .findFirst()
        .ifPresent(
            q ->
                result.add(
                    map(
                        "kind",
                        "quiz",
                        "recommendation_type",
                        "general_quiz",
                        "title",
                        q.get("title"),
                        "subtitle",
                        "A general challenge to sharpen your skills",
                        "action_label",
                        "Take Quiz",
                        "quiz_type",
                        "general")));
    if (ongoing.isPresent())
      quizzes.stream()
          .filter(
              q ->
                  Objects.equals(q.get("book_id"), ongoing.get().get("book_id"))
                      && !"general".equals(q.get("type"))
                      && !taken.contains(q.get("id").toString()))
          .findFirst()
          .ifPresent(
              q ->
                  result.add(
                      map(
                          "kind",
                          "quiz",
                          "recommendation_type",
                          "book_quiz",
                          "title",
                          q.get("title"),
                          "subtitle",
                          "Test what you learned from your current book",
                          "action_label",
                          "Practice",
                          "book_id",
                          q.get("book_id"),
                          "quiz_type",
                          "book")));
    return result.stream().limit(4).toList();
  }

  private Map<String, Object> bookRecommendation(
      Map<String, Object> b, String type, String title, String subtitle, String action) {
    return map(
        "kind",
        "book",
        "recommendation_type",
        type,
        "title",
        title,
        "subtitle",
        subtitle,
        "action_label",
        action,
        "book_id",
        b.get("id"),
        "cover_url",
        b.get("cover_url"),
        "file_url",
        null,
        "format_type",
        b.get("format_type"));
  }

  public Object stats() {
    OffsetDateTime today = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
    long quizzes = db.count("select count(*) from quizzes"),
        general = db.count("select count(*) from quizzes where type='general'");
    long attempts = db.count("select count(*) from quiz_attempts");
    Object average =
        db.one("select coalesce(round(avg(score)::numeric,1),0) as value from quiz_attempts")
            .get("value");
    return map(
        "total_books",
        db.count("select count(*) from books"),
        "total_users",
        db.count("select count(*) from profiles"),
        "total_quizzes",
        quizzes,
        "active_readers",
        db.count("select count(distinct user_id) from reading_sessions"),
        "total_reading_sessions",
        db.count("select count(*) from reading_sessions"),
        "reading_minutes_today",
        db.count(
            "select coalesce(sum(minutes_spent),0) from reading_sessions where started_at>=?",
            today),
        "active_readers_7d",
        db.count(
            "select count(distinct user_id) from reading_sessions where started_at>=?",
            OffsetDateTime.now().minusDays(7)),
        "completed_books",
        db.count("select count(*) from user_progress where is_completed=true"),
        "quiz_attempts",
        attempts,
        "total_quiz_results",
        attempts,
        "average_quiz_score",
        average,
        "general_quizzes",
        general,
        "book_quizzes",
        quizzes - general);
  }

  public Object users() {
    return db.list(
        "select id,email,full_name,avatar_url,role,created_at,class,learning_goal from profiles"
            + " order by created_at desc,id");
  }

  public Object userProgress(UUID user) {
    db.byId("profiles", user);
    var sessions = learning.ownedList("reading_sessions", user, null);
    var attempts =
        db.list("select * from quiz_attempts where user_id=? order by completed_at desc,id", user);
    for (var a : attempts) a.put("quizzes", db.byId("quizzes", uuid(a.get("quiz_id"))));
    long minutes =
        sessions.stream().mapToLong(s -> ((Number) s.get("minutes_spent")).longValue()).sum();
    long days =
        sessions.stream()
            .map(s -> AssessmentService.instant(s.get("started_at")).atZone(zone).toLocalDate())
            .distinct()
            .count();
    return map(
        "book_progress",
        learning.progress(user),
        "reading_sessions",
        sessions.stream().limit(20).toList(),
        "quiz_results",
        attempts,
        "quiz_attempts",
        attempts,
        "total_reading_minutes",
        minutes,
        "active_reading_days",
        days);
  }
}
