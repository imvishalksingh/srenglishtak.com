package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "daily_tips")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class DailyTipsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "title", columnDefinition = "text")
  @JsonProperty("title")
  public String title;

  @Column(name = "content", columnDefinition = "text")
  @JsonProperty("content")
  public String content;

  @Column(name = "author", columnDefinition = "text")
  @JsonProperty("author")
  public String author;

  @Column(name = "image_url", columnDefinition = "text")
  @JsonProperty("image_url")
  public String image_url;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
