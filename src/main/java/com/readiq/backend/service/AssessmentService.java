package com.readiq.backend.service;

import static com.readiq.backend.api.Input.*;

import com.readiq.backend.api.*;
import com.readiq.backend.data.Database;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {
  private final Database db;

  public AssessmentService(Database db) {
    this.db = db;
  }

  public List<Map<String, Object>> quizzes(UUID user, boolean admin, UUID book) {
    var rows =
        db.list(
            "select * from quizzes where (? or is_visible=true)"
                + (book == null ? "" : " and book_id=?")
                + " order by created_at desc,id",
            book == null ? new Object[] {admin} : new Object[] {admin, book});
    for (var row : rows) {
      UUID id = uuid(row.get("id"));
      enrichQuiz(row, admin);
      row.put(
          "has_submitted",
          user != null
              && db.count(
                      "select count(*) from quiz_attempts where user_id=? and quiz_id=?", user, id)
                  > 0);
    }
    return rows;
  }

  public Map<String, Object> quiz(UUID id, boolean admin) {
    var row = db.byId("quizzes", id);
    if (!admin && !Boolean.TRUE.equals(row.get("is_visible"))) throw ApiException.missing();
    enrichQuiz(row, admin);
    return row;
  }

  private void enrichQuiz(Map<String, Object> row, boolean admin) {
    row.put("questions", questions(false, uuid(row.get("id")), admin));
  }

  public List<Map<String, Object>> questions(boolean challenge, UUID id, boolean admin) {
    var rows =
        db.list(
            "select * from "
                + (challenge ? "challenge_questions where challenge_id" : "questions where quiz_id")
                + "=? order by question_order,created_at,id",
            id);
    if (!admin)
      for (var row : rows) {
        row.remove("correct_option_index");
        row.remove("explanation");
      }
    return rows;
  }

  @Transactional
  public Map<String, Object> saveQuiz(UUID id, Map<String, Object> b) {
    if (id != null) db.one("select * from quizzes where id=? for update", id);
    var values =
        fields(b, "title", "description", "duration_minutes", "type", "book_id", "is_visible");
    if (id == null || b.containsKey("title")) text(b, "title", 300, true);
    choice(values, "type", "book", "grammar", "general");
    if (values.containsKey("duration_minutes"))
      integer(values.get("duration_minutes"), "duration_minutes", 1, 1440);
    if ("general".equals(values.get("type"))) values.put("book_id", null);
    if (values.get("book_id") != null) db.byId("books", (UUID) values.get("book_id"));
    if (id == null && !values.containsKey("type"))
      values.put("type", values.get("book_id") == null ? "general" : "book");
    var row = id == null ? db.insert("quizzes", values) : db.update("quizzes", id, values);
    UUID quizId = uuid(row.get("id"));
    if (b.containsKey("questions")) {
      var rows = objects(b.get("questions"), "questions", 200);
      var validated = validateQuestions(rows, false);
      db.execute("delete from questions where quiz_id=?", quizId);
      for (var q : validated) {
        q.put("quiz_id", quizId);
        db.insert("questions", q);
      }
    }
    return quiz(quizId, true);
  }

  @Transactional
  public Map<String, Object> addQuestion(UUID quiz, Map<String, Object> b) {
    db.one("select id from quizzes where id=? for update", quiz);
    var q = validateQuestions(List.of(b), false).getFirst();
    q.put("quiz_id", quiz);
    q.put(
        "question_order",
        Math.toIntExact(db.count("select count(*) from questions where quiz_id=?", quiz)));
    return db.insert("questions", q);
  }

  @Transactional
  public void deleteQuiz(UUID id) {
    db.delete("quizzes", id);
  }

  @Transactional
  public Map<String, Object> submitQuiz(UUID user, UUID id, Map<String, Object> b) {
    db.one("select id from quizzes where id=? and is_visible=true for share", id);
    var questions = questions(false, id, true);
    var scored = score(questions, b, false);
    var attempt =
        db.insert(
            "quiz_attempts",
            map(
                "user_id",
                user,
                "quiz_id",
                id,
                "score",
                scored.correct,
                "total_questions",
                questions.size(),
                "answers_json",
                scored.details));
    db.insert("quiz_results", map("user_id", user, "quiz_id", id, "score", scored.correct));
    return map(
        "attempt_id",
        attempt.get("id"),
        "score",
        scored.correct,
        "total",
        questions.size(),
        "completed_at",
        attempt.get("completed_at"));
  }

  public List<Map<String, Object>> challenges(UUID user, boolean admin) {
    var rows = db.list("select * from challenges order by created_at desc,id");
    for (var row : rows) {
      UUID id = uuid(row.get("id"));
      row.put(
          "question_count",
          db.count("select count(*) from challenge_questions where challenge_id=?", id));
      row.put("challenge_questions", admin ? questions(true, id, true) : List.of());
      row.put(
          "has_submitted",
          user != null
              && db.count(
                      "select count(*) from challenge_results where challenge_id=? and user_id=?",
                      id,
                      user)
                  > 0);
    }
    return rows;
  }

  public Map<String, Object> challenge(UUID id, boolean admin) {
    var row = db.byId("challenges", id);
    row.put(
        "question_count",
        db.count("select count(*) from challenge_questions where challenge_id=?", id));
    row.put("challenge_questions", admin ? questions(true, id, true) : List.of());
    return row;
  }

  @Transactional
  public Map<String, Object> saveChallenge(UUID id, Map<String, Object> b) {
    if (id != null) db.one("select id from challenges where id=? for update", id);
    var values =
        fields(
            b,
            "name",
            "description",
            "prize_text",
            "image_url",
            "start_time",
            "end_time",
            "duration_minutes");
    if (id == null || b.containsKey("name")) text(b, "name", 300, true);
    if (values.containsKey("duration_minutes"))
      integer(values.get("duration_minutes"), "duration_minutes", 1, 1440);
    var existing = id == null ? Map.<String, Object>of() : db.byId("challenges", id);
    var start = instant(values.getOrDefault("start_time", existing.get("start_time")));
    var end = instant(values.getOrDefault("end_time", existing.get("end_time")));
    if (start != null && end != null && !end.isAfter(start))
      throw ApiException.bad("end_time must follow start_time");
    if (id != null
        && db.count("select count(*) from challenge_attempt_sessions where challenge_id=?", id) > 0)
      throw new ApiException(409, "A challenge with started attempts cannot be edited");
    var row = id == null ? db.insert("challenges", values) : db.update("challenges", id, values);
    UUID challengeId = uuid(row.get("id"));
    if (b.containsKey("questions")) {
      var rows = validateQuestions(objects(b.get("questions"), "questions", 200), true);
      db.execute("delete from challenge_questions where challenge_id=?", challengeId);
      for (var q : rows) {
        q.put("challenge_id", challengeId);
        db.insert("challenge_questions", q);
      }
    }
    return challenge(challengeId, true);
  }

  @Transactional
  public void deleteChallenge(UUID id) {
    db.delete("challenges", id);
  }

  @Transactional
  public Map<String, Object> start(UUID user, UUID challenge) {
    var c = db.one("select * from challenges where id=? for update", challenge);
    checkWindow(c);
    if (db.count(
            "select count(*) from challenge_results where user_id=? and challenge_id=?",
            user,
            challenge)
        > 0) throw new ApiException(409, "You have already submitted this challenge");
    if (db.count("select count(*) from challenge_questions where challenge_id=?", challenge) == 0)
      throw ApiException.bad("Challenge has no questions");
    var old =
        db.optional(
            "select * from challenge_attempt_sessions where user_id=? and challenge_id=?",
            user,
            challenge);
    Map<String, Object> attempt;
    if (old.isPresent()) attempt = old.get();
    else {
      Instant now = Instant.now();
      long minutes = c.get("duration_minutes") instanceof Number n ? n.longValue() : 5;
      Instant deadline = now.plusSeconds(minutes * 60);
      Instant end = instant(c.get("end_time"));
      if (end != null && end.isBefore(deadline)) deadline = end;
      attempt =
          db.insert(
              "challenge_attempt_sessions",
              map(
                  "user_id",
                  user,
                  "challenge_id",
                  challenge,
                  "started_at",
                  OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                  "expires_at",
                  OffsetDateTime.ofInstant(deadline, ZoneOffset.UTC)));
    }
    if (Instant.now().isAfter(instant(attempt.get("expires_at"))))
      throw new ApiException(410, "Challenge attempt has expired");
    var content = db.byId("challenges", challenge);
    content.put("challenge_questions", questions(true, challenge, false));
    return map(
        "started_at",
        attempt.get("started_at"),
        "expires_at",
        attempt.get("expires_at"),
        "challenge",
        content);
  }

  @Transactional
  public Map<String, Object> submitChallenge(UUID user, UUID challenge, Map<String, Object> b) {
    var c = db.one("select * from challenges where id=? for share", challenge);
    var a =
        db.one(
            "select * from challenge_attempt_sessions where challenge_id=? and user_id=? for"
                + " update",
            challenge,
            user);
    if (db.count(
            "select count(*) from challenge_results where user_id=? and challenge_id=?",
            user,
            challenge)
        > 0) throw new ApiException(409, "You have already submitted this challenge");
    if (Instant.now().isAfter(instant(a.get("expires_at")).plusSeconds(10)))
      throw new ApiException(410, "Challenge attempt has expired");
    if (b.containsKey("score"))
      throw ApiException.bad("Send answers; scores are calculated by the server");
    var scored = score(questions(true, challenge, true), b, true);
    long elapsed =
        Math.max(0, Duration.between(instant(a.get("started_at")), Instant.now()).toMillis());
    return db.insert(
        "challenge_results",
        map(
            "user_id",
            user,
            "challenge_id",
            challenge,
            "score",
            scored.correct * 10,
            "time_taken_ms",
            (int) Math.min(Integer.MAX_VALUE, elapsed)));
  }

  public List<Map<String, Object>> leaderboard(UUID id) {
    db.byId("challenges", id);
    var rows =
        db.list(
            "select r.id,r.score,r.time_taken_ms,r.completed_at,p.id as"
                + " profile_id,p.full_name,p.avatar_url from challenge_results r join profiles p on"
                + " p.id=r.user_id where r.challenge_id=? order by r.score"
                + " desc,r.time_taken_ms,r.completed_at,r.id",
            id);
    for (var row : rows)
      row.put(
          "profiles",
          map(
              "id",
              row.remove("profile_id"),
              "full_name",
              row.remove("full_name"),
              "avatar_url",
              row.remove("avatar_url")));
    return rows;
  }

  private void checkWindow(Map<String, Object> c) {
    Instant now = Instant.now(),
        start = instant(c.get("start_time")),
        end = instant(c.get("end_time"));
    if (start != null && now.isBefore(start))
      throw new ApiException(409, "Challenge has not started");
    if (end != null && now.isAfter(end)) throw new ApiException(410, "Challenge has ended");
  }

  private record Scored(int correct, List<Map<String, Object>> details) {}

  private Scored score(
      List<Map<String, Object>> questions, Map<String, Object> b, boolean partial) {
    if (questions.isEmpty()) throw ApiException.bad("No questions available");
    var answers = objects(b.get("answers"), "answers", 200);
    var selected = new HashMap<UUID, Integer>();
    var known = new HashSet<UUID>();
    for (var q : questions) known.add(uuid(q.get("id")));
    for (var answer : answers) {
      UUID id = uuid(answer.get("question_id"));
      int option = integer(answer.get("selected_option_index"), "selected_option_index", 0, 3);
      if (!known.contains(id) || selected.put(id, option) != null)
        throw ApiException.bad("Invalid or duplicate question_id");
    }
    if (!partial && selected.size() != questions.size())
      throw ApiException.bad("An answer is required for every question");
    int correct = 0;
    var details = new ArrayList<Map<String, Object>>();
    for (var q : questions) {
      UUID id = uuid(q.get("id"));
      Integer choice = selected.get(id);
      boolean right =
          choice != null && choice.equals(((Number) q.get("correct_option_index")).intValue());
      if (right) correct++;
      details.add(map("question_id", id, "selected_option_index", choice, "is_correct", right));
    }
    return new Scored(correct, details);
  }

  private List<Map<String, Object>> validateQuestions(
      List<Map<String, Object>> rows, boolean challenge) {
    var result = new ArrayList<Map<String, Object>>();
    int order = 0;
    for (var b : rows) {
      var q = fields(b, "question_text", "image_url", "explanation", "category");
      text(b, "question_text", 10000, true);
      if (!challenge) q.remove("category");
      else {
        q.remove("image_url");
        q.remove("explanation");
      }
      Object raw = b.get("options");
      if (!(raw instanceof List<?> options) || options.size() != 4)
        throw ApiException.bad("Exactly four options are required");
      var seen = new HashSet<String>();
      var values = new ArrayList<Object>();
      for (int i = 0; i < 4; i++) {
        Object option = options.get(i);
        String value;
        if (challenge) {
          if (!(option instanceof String s)) throw ApiException.bad("Invalid option");
          value = s;
          values.add(s);
        } else {
          if (!(option instanceof Map<?, ?> m) || !(m.get("text") instanceof String s))
            throw ApiException.bad("Invalid option");
          value = s;
          values.add(map("label", Character.toString('A' + i), "text", s));
        }
        if (value.isBlank()
            || value.length() > 2000
            || !seen.add(value.strip().toLowerCase(Locale.ROOT)))
          throw ApiException.bad("Options must contain distinct non-empty text");
      }
      q.put("options", values);
      q.put(
          "correct_option_index",
          integer(b.get("correct_option_index"), "correct_option_index", 0, 3));
      q.put("question_order", order++);
      result.add(q);
    }
    return result;
  }

  public static Instant instant(Object v) {
    return v == null
        ? null
        : v instanceof OffsetDateTime t ? t.toInstant() : Instant.parse(v.toString());
  }
}
