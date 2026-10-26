package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "challenge_results")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class ChallengeResultsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "user_id")
  @JsonProperty("user_id")
  public UUID user_id;

  @Column(name = "challenge_id")
  @JsonProperty("challenge_id")
  public UUID challenge_id;

  @Column(name = "score")
  @JsonProperty("score")
  public Integer score;

  @Column(name = "time_taken_ms")
  @JsonProperty("time_taken_ms")
  public Integer time_taken_ms;

  @Column(name = "completed_at")
  @JsonProperty("completed_at")
  public OffsetDateTime completed_at;
}
