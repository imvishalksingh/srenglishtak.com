package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "grammar_units")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class GrammarUnitsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "title", columnDefinition = "text")
  @JsonProperty("title")
  public String title;

  @Column(name = "description", columnDefinition = "text")
  @JsonProperty("description")
  public String description;

  @Column(name = "unit_order")
  @JsonProperty("unit_order")
  public Integer unit_order;

  @Column(name = "image_url", columnDefinition = "text")
  @JsonProperty("image_url")
  public String image_url;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
