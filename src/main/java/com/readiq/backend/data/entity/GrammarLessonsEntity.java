package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "grammar_lessons")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class GrammarLessonsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "unit_id")
  @JsonProperty("unit_id")
  public UUID unit_id;

  @Column(name = "title", columnDefinition = "text")
  @JsonProperty("title")
  public String title;

  @Column(name = "content_type", columnDefinition = "text")
  @JsonProperty("content_type")
  public String content_type;

  @Column(name = "content_data", columnDefinition = "text")
  @JsonProperty("content_data")
  public String content_data;

  @Column(name = "lesson_order")
  @JsonProperty("lesson_order")
  public Integer lesson_order;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
