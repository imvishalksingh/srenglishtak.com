package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "user_progress")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class UserProgressEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "user_id")
  @JsonProperty("user_id")
  public UUID user_id;

  @Column(name = "book_id")
  @JsonProperty("book_id")
  public UUID book_id;

  @Column(name = "current_page")
  @JsonProperty("current_page")
  public Integer current_page;

  @Column(name = "progress_percent", precision = 5, scale = 2)
  @JsonProperty("progress_percent")
  public BigDecimal progress_percent;

  @Column(name = "is_completed")
  @JsonProperty("is_completed")
  public Boolean is_completed;

  @Column(name = "total_minutes_read")
  @JsonProperty("total_minutes_read")
  public Integer total_minutes_read;

  @Column(name = "started_at")
  @JsonProperty("started_at")
  public OffsetDateTime started_at;

  @Column(name = "completed_at")
  @JsonProperty("completed_at")
  public OffsetDateTime completed_at;

  @Column(name = "last_position", columnDefinition = "text")
  @JsonProperty("last_position")
  public String last_position;

  @Column(name = "last_opened_at")
  @JsonProperty("last_opened_at")
  public OffsetDateTime last_opened_at;

  @Column(name = "last_read_at")
  @JsonProperty("last_read_at")
  public OffsetDateTime last_read_at;
}
