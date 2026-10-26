package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "user_achievements")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class UserAchievementsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "user_id")
  @JsonProperty("user_id")
  public UUID user_id;

  @Column(name = "code", columnDefinition = "text")
  @JsonProperty("code")
  public String code;

  @Column(name = "title", columnDefinition = "text")
  @JsonProperty("title")
  public String title;

  @Column(name = "description", columnDefinition = "text")
  @JsonProperty("description")
  public String description;

  @Column(name = "unlocked_at")
  @JsonProperty("unlocked_at")
  public OffsetDateTime unlocked_at;
}
