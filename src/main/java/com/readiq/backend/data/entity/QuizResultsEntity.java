package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "quiz_results")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class QuizResultsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "user_id")
  @JsonProperty("user_id")
  public UUID user_id;

  @Column(name = "quiz_id")
  @JsonProperty("quiz_id")
  public UUID quiz_id;

  @Column(name = "score")
  @JsonProperty("score")
  public Integer score;

  @Column(name = "completed_at")
  @JsonProperty("completed_at")
  public OffsetDateTime completed_at;
}
