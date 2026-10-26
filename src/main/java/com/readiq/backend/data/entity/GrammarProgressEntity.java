package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "grammar_progress")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class GrammarProgressEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "user_id")
  @JsonProperty("user_id")
  public UUID user_id;

  @Column(name = "lesson_id")
  @JsonProperty("lesson_id")
  public UUID lesson_id;

  @Column(name = "is_completed")
  @JsonProperty("is_completed")
  public Boolean is_completed;

  @Column(name = "completed_at")
  @JsonProperty("completed_at")
  public OffsetDateTime completed_at;
}
