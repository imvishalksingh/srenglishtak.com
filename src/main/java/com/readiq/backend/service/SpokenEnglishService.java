package com.readiq.backend.service;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.data.Database;
import com.readiq.backend.dto.SpokenLessonRequest;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpokenEnglishService {
  private final Database db;
  private static final String SUMMARY = "id,title,category,summary,lesson_order,is_published,created_at,updated_at";
  public SpokenEnglishService(Database db) { this.db = db; }

  public List<Map<String,Object>> lessons(String category, boolean admin) {
    if (category != null && !Set.of("conversation","pronunciation","speaking").contains(category))
      throw ApiException.bad("Choose a valid lesson category");
    String sql = "select " + SUMMARY + " from spoken_english_lessons where "
        + (admin ? "true" : "is_published=true")
        + (category == null ? "" : " and category=?")
        + " order by category,lesson_order,created_at,id";
    return category == null ? db.list(sql) : db.list(sql,category);
  }

  public Map<String,Object> lesson(UUID id, boolean admin) {
    return db.one("select " + SUMMARY + ",content from spoken_english_lessons where id=?"
        + (admin ? "" : " and is_published=true"), id);
  }

  @Transactional
  public Map<String,Object> save(UUID id, SpokenLessonRequest request) {
    var fields = new LinkedHashMap<String,Object>();
    fields.put("title",request.title().strip());
    fields.put("category",request.category());
    fields.put("summary",request.summary()==null ? "" : request.summary().strip());
    fields.put("content",request.content().strip());
    fields.put("lesson_order",request.lesson_order()==null ? 0 : request.lesson_order());
    fields.put("is_published",Boolean.TRUE.equals(request.is_published()));
    fields.put("updated_at",OffsetDateTime.now());
    return id == null ? db.insert("spoken_english_lessons",fields)
        : db.update("spoken_english_lessons",id,fields);
  }

  @Transactional
  public void delete(UUID id) { db.delete("spoken_english_lessons",id); }
}
