package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "cbse_categories")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class CbseCategoriesEntity {
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

  @Column(name = "icon_url", columnDefinition = "text")
  @JsonProperty("icon_url")
  public String icon_url;

  @Column(name = "order_index")
  @JsonProperty("order_index")
  public Integer order_index;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
