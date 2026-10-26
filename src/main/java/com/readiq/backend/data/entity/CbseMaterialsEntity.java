package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "cbse_materials")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class CbseMaterialsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "category_id")
  @JsonProperty("category_id")
  public UUID category_id;

  @Column(name = "material_type", columnDefinition = "text")
  @JsonProperty("material_type")
  public String material_type;

  @Column(name = "title", columnDefinition = "text")
  @JsonProperty("title")
  public String title;

  @Column(name = "year", columnDefinition = "text")
  @JsonProperty("year")
  public String year;

  @Column(name = "description", columnDefinition = "text")
  @JsonProperty("description")
  public String description;

  @Column(name = "file_url", columnDefinition = "text")
  @JsonProperty("file_url")
  public String file_url;

  @Column(name = "thumbnail_url", columnDefinition = "text")
  @JsonProperty("thumbnail_url")
  public String thumbnail_url;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
