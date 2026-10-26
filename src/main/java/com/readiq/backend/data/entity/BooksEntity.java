package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "books")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class BooksEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "title", columnDefinition = "text")
  @JsonProperty("title")
  public String title;

  @Column(name = "author", columnDefinition = "text")
  @JsonProperty("author")
  public String author;

  @Column(name = "description", columnDefinition = "text")
  @JsonProperty("description")
  public String description;

  @Column(name = "category", columnDefinition = "text")
  @JsonProperty("category")
  public String category;

  @Column(name = "format_type", columnDefinition = "text")
  @JsonProperty("format_type")
  public String format_type;

  @Column(name = "file_url", columnDefinition = "text")
  @JsonProperty("file_url")
  public String file_url;

  @Column(name = "cover_url", columnDefinition = "text")
  @JsonProperty("cover_url")
  public String cover_url;

  @Column(name = "is_visible")
  @JsonProperty("is_visible")
  public Boolean is_visible;

  @Column(name = "uploaded_by")
  @JsonProperty("uploaded_by")
  public UUID uploaded_by;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;

  @Column(name = "price_paise")
  @JsonProperty("price_paise")
  public Integer price_paise;

  @Column(name = "currency", columnDefinition = "text")
  @JsonProperty("currency")
  public String currency;

  @Column(name = "android_product_id", columnDefinition = "text")
  @JsonProperty("android_product_id")
  public String android_product_id;
}
