package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "reading_sessions")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class ReadingSessionsEntity {
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

  @Column(name = "started_at")
  @JsonProperty("started_at")
  public OffsetDateTime started_at;

  @Column(name = "ended_at")
  @JsonProperty("ended_at")
  public OffsetDateTime ended_at;

  @Column(name = "start_page")
  @JsonProperty("start_page")
  public Integer start_page;

  @Column(name = "end_page")
  @JsonProperty("end_page")
  public Integer end_page;

  @Column(name = "pages_read")
  @JsonProperty("pages_read")
  public Integer pages_read;

  @Column(name = "minutes_spent")
  @JsonProperty("minutes_spent")
  public Integer minutes_spent;

  @Column(name = "device_type", columnDefinition = "text")
  @JsonProperty("device_type")
  public String device_type;

  @Column(name = "source", columnDefinition = "text")
  @JsonProperty("source")
  public String source;

  @Column(name = "is_completed")
  @JsonProperty("is_completed")
  public Boolean is_completed;
}
