package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "quizzes")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class QuizzesEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "book_id")
  @JsonProperty("book_id")
  public UUID book_id;

  @Column(name = "type", columnDefinition = "text")
  @JsonProperty("type")
  public String type;

  @Column(name = "title", columnDefinition = "text")
  @JsonProperty("title")
  public String title;

  @Column(name = "description", columnDefinition = "text")
  @JsonProperty("description")
  public String description;

  @Column(name = "duration_minutes")
  @JsonProperty("duration_minutes")
  public Integer duration_minutes;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;

  @Column(name = "is_visible")
  @JsonProperty("is_visible")
  public Boolean is_visible;
}
