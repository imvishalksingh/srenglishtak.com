package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "questions")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class QuestionsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "quiz_id")
  @JsonProperty("quiz_id")
  public UUID quiz_id;

  @Column(name = "question_text", columnDefinition = "text")
  @JsonProperty("question_text")
  public String question_text;

  @Column(name = "image_url", columnDefinition = "text")
  @JsonProperty("image_url")
  public String image_url;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "options", columnDefinition = "jsonb")
  @JsonProperty("options")
  public java.util.List<Object> options;

  @Column(name = "correct_option_index")
  @JsonProperty("correct_option_index")
  public Integer correct_option_index;

  @Column(name = "explanation", columnDefinition = "text")
  @JsonProperty("explanation")
  public String explanation;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;

  @Column(name = "question_order")
  @JsonProperty("question_order")
  public Integer question_order;
}
