package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "notes")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class NotesEntity {
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

  @Column(name = "page")
  @JsonProperty("page")
  public Integer page;

  @Column(name = "position", columnDefinition = "text")
  @JsonProperty("position")
  public String position;

  @Column(name = "note_text", columnDefinition = "text")
  @JsonProperty("note_text")
  public String note_text;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;

  @Column(name = "updated_at")
  @JsonProperty("updated_at")
  public OffsetDateTime updated_at;
}
