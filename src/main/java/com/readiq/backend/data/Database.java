package com.readiq.backend.data;

import com.readiq.backend.api.ApiException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class Database {
  private final JdbcTemplate jdbc;
  private final JpaRecordStore records;
  private final ObjectMapper json;
  private static final Set<String> TABLES =
      Set.of(
          "profiles",
          "books",
          "quizzes",
          "questions",
          "user_progress",
          "reading_sessions",
          "bookmarks",
          "notes",
          "user_achievements",
          "quiz_results",
          "quiz_attempts",
          "vocabulary",
          "daily_tips",
          "challenges",
          "challenge_questions",
          "challenge_results",
          "grammar_units",
          "grammar_lessons",
          "grammar_progress",
          "cbse_categories",
          "cbse_materials",
          "module_interests",
          "challenge_attempt_sessions");

  public Database(JdbcTemplate jdbc, ObjectMapper json, JpaRecordStore records) {
    this.jdbc = jdbc;
    this.json = json;
    this.records = records;
  }

  private Map<String, Object> mapRow(ResultSet rs, int n) throws SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    ResultSetMetaData meta = rs.getMetaData();
    for (int i = 1; i <= meta.getColumnCount(); i++) {
      Object v = rs.getObject(i);
      if (v instanceof Timestamp t) v = t.toInstant().toString();
      else if (v instanceof OffsetDateTime t) v = t.toInstant().toString();
      else if (v instanceof UUID) v = v.toString();
      else if ("jsonb".equals(meta.getColumnTypeName(i))
          || "json".equals(meta.getColumnTypeName(i))) {
        try {
          v = v == null ? null : json.readValue(v.toString(), Object.class);
        } catch (Exception ex) {
          throw new SQLException("Invalid stored JSON", ex);
        }
      }
      row.put(meta.getColumnLabel(i), v);
    }
    return row;
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.query(sql, this::mapRow, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = list(sql, args);
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  public Optional<Map<String, Object>> optional(String sql, Object... args) {
    return list(sql, args).stream().findFirst();
  }

  public long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  public int execute(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public Map<String, Object> byId(String table, UUID id) {
    return one("select * from " + table(table) + " where id=?", id);
  }

  public Map<String, Object> insert(String table, Map<String, Object> values) {
    table(table);
    if (values.isEmpty()) throw ApiException.bad("Empty request");
    return records.insert(table, values);
  }

  public Map<String, Object> update(String table, UUID id, Map<String, Object> values) {
    table(table);
    return records.update(table, id, values);
  }

  public void delete(String table, UUID id) {
    if (execute("delete from " + table(table) + " where id=?", id) == 0)
      throw ApiException.missing();
  }

  private static String table(String table) {
    if (!TABLES.contains(table)) throw new IllegalArgumentException("Unknown table");
    return table;
  }

  private static void column(String column) {
    if (!column.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid column");
  }
}
