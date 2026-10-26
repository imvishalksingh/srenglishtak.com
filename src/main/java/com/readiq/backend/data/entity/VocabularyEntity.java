package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "vocabulary")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class VocabularyEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "word", columnDefinition = "text")
  @JsonProperty("word")
  public String word;

  @Column(name = "meaning", columnDefinition = "text")
  @JsonProperty("meaning")
  public String meaning;

  @Column(name = "example_sentence", columnDefinition = "text")
  @JsonProperty("example_sentence")
  public String example_sentence;

  @Column(name = "category", columnDefinition = "text")
  @JsonProperty("category")
  public String category;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
