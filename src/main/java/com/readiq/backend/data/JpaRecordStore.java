package com.readiq.backend.data;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.data.entity.*;
import jakarta.persistence.*;
import java.lang.reflect.Field;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Entity writes share the same transaction/connection as JDBC scoring and reporting queries.
 * Entities never cross the HTTP boundary. Explicit DTO/service allowlists select writable fields.
 */
@Repository
public class JpaRecordStore {
  private final EntityManager em;
  private final ObjectMapper json;
  private static final Map<String, Class<?>> TYPES =
      Map.ofEntries(
          Map.entry("profiles", ProfilesEntity.class),
          Map.entry("books", BooksEntity.class),
          Map.entry("quizzes", QuizzesEntity.class),
          Map.entry("questions", QuestionsEntity.class),
          Map.entry("user_progress", UserProgressEntity.class),
          Map.entry("reading_sessions", ReadingSessionsEntity.class),
          Map.entry("bookmarks", BookmarksEntity.class),
          Map.entry("notes", NotesEntity.class),
          Map.entry("user_achievements", UserAchievementsEntity.class),
          Map.entry("quiz_results", QuizResultsEntity.class),
          Map.entry("quiz_attempts", QuizAttemptsEntity.class),
          Map.entry("vocabulary", VocabularyEntity.class),
          Map.entry("daily_tips", DailyTipsEntity.class),
          Map.entry("challenges", ChallengesEntity.class),
          Map.entry("challenge_questions", ChallengeQuestionsEntity.class),
          Map.entry("challenge_results", ChallengeResultsEntity.class),
          Map.entry("grammar_units", GrammarUnitsEntity.class),
          Map.entry("grammar_lessons", GrammarLessonsEntity.class),
          Map.entry("grammar_progress", GrammarProgressEntity.class),
          Map.entry("cbse_categories", CbseCategoriesEntity.class),
          Map.entry("cbse_materials", CbseMaterialsEntity.class),
          Map.entry("module_interests", ModuleInterestsEntity.class),
          Map.entry("challenge_attempt_sessions", ChallengeAttemptSessionsEntity.class));

  public JpaRecordStore(EntityManager em, ObjectMapper json) {
    this.em = em;
    this.json = json;
  }

  @Transactional
  public Map<String, Object> insert(String table, Map<String, Object> values) {
    Class<?> type = type(table);
    Object entity = json.convertValue(values, type);
    em.persist(entity);
    em.flush();
    em.refresh(entity); // Return database defaults, preserving the existing flat API contract.
    var result = row(entity);
    em.detach(entity);
    return result;
  }

  @Transactional
  public Map<String, Object> update(String table, UUID id, Map<String, Object> values) {
    Class<?> type = type(table);
    Object entity = em.find(type, id);
    if (entity == null) throw ApiException.missing();
    for (var entry : values.entrySet()) {
      if (entry.getKey().equals("id")) throw ApiException.bad("Identifier cannot be changed");
      try {
        Field field =
            type.getField(entry.getKey().equals("class") ? "studentClass" : entry.getKey());
        field.set(entity, json.convertValue(entry.getValue(), field.getType()));
      } catch (ReflectiveOperationException ex) {
        throw ApiException.bad("Invalid field");
      }
    }
    em.flush();
    em.refresh(entity);
    var result = row(entity);
    em.detach(entity);
    return result;
  }

  private Class<?> type(String table) {
    Class<?> type = TYPES.get(table);
    if (type == null) throw new IllegalArgumentException("Unknown entity");
    return type;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> row(Object entity) {
    return json.convertValue(entity, LinkedHashMap.class);
  }
}
