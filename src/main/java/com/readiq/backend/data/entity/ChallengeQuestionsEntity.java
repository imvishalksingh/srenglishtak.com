package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "challenge_questions")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class ChallengeQuestionsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "challenge_id")
  @JsonProperty("challenge_id")
  public UUID challenge_id;

  @Column(name = "category", columnDefinition = "text")
  @JsonProperty("category")
  public String category;

  @Column(name = "question_text", columnDefinition = "text")
  @JsonProperty("question_text")
  public String question_text;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "options", columnDefinition = "jsonb")
  @JsonProperty("options")
  public java.util.List<Object> options;

  @Column(name = "correct_option_index")
  @JsonProperty("correct_option_index")
  public Integer correct_option_index;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;

  @Column(name = "question_order")
  @JsonProperty("question_order")
  public Integer question_order;
}
