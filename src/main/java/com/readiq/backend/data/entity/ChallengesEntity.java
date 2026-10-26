package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "challenges")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class ChallengesEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "name", columnDefinition = "text")
  @JsonProperty("name")
  public String name;

  @Column(name = "description", columnDefinition = "text")
  @JsonProperty("description")
  public String description;

  @Column(name = "prize_text", columnDefinition = "text")
  @JsonProperty("prize_text")
  public String prize_text;

  @Column(name = "image_url", columnDefinition = "text")
  @JsonProperty("image_url")
  public String image_url;

  @Column(name = "start_time")
  @JsonProperty("start_time")
  public OffsetDateTime start_time;

  @Column(name = "end_time")
  @JsonProperty("end_time")
  public OffsetDateTime end_time;

  @Column(name = "duration_minutes")
  @JsonProperty("duration_minutes")
  public Integer duration_minutes;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;

  @Column(name = "updated_at")
  @JsonProperty("updated_at")
  public OffsetDateTime updated_at;
}
