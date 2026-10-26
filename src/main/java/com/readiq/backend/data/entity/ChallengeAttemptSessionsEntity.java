package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "challenge_attempt_sessions")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class ChallengeAttemptSessionsEntity {
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

  @Column(name = "started_at")
  @JsonProperty("started_at")
  public OffsetDateTime started_at;

  @Column(name = "expires_at")
  @JsonProperty("expires_at")
  public OffsetDateTime expires_at;
}
