package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class BookRequest extends Payload {
  private String title;

  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  @JsonProperty("title")
  public void setTitle(String value) {
    this.title = value;
    set("title", value);
  }

  private String author;

  @JsonProperty("author")
  public String getAuthor() {
    return author;
  }

  @JsonProperty("author")
  public void setAuthor(String value) {
    this.author = value;
    set("author", value);
  }

  private String description;

  @JsonProperty("description")
  public String getDescription() {
    return description;
  }

  @JsonProperty("description")
  public void setDescription(String value) {
    this.description = value;
    set("description", value);
  }

  private String category;

  @JsonProperty("category")
  public String getCategory() {
    return category;
  }

  @JsonProperty("category")
  public void setCategory(String value) {
    this.category = value;
    set("category", value);
  }

  private String format_type;

  @JsonProperty("format_type")
  public String getFormat_type() {
    return format_type;
  }

  @JsonProperty("format_type")
  public void setFormat_type(String value) {
    this.format_type = value;
    set("format_type", value);
  }

  private String file_url;

  @JsonProperty("file_url")
  public String getFile_url() {
    return file_url;
  }

  @JsonProperty("file_url")
  public void setFile_url(String value) {
    this.file_url = value;
    set("file_url", value);
  }

  private String cover_url;

  @JsonProperty("cover_url")
  public String getCover_url() {
    return cover_url;
  }

  @JsonProperty("cover_url")
  public void setCover_url(String value) {
    this.cover_url = value;
    set("cover_url", value);
  }

  private Boolean is_visible;

  @JsonProperty("is_visible")
  public Boolean getIs_visible() {
    return is_visible;
  }

  @JsonProperty("is_visible")
  public void setIs_visible(Boolean value) {
    this.is_visible = value;
    set("is_visible", value);
  }

  private Integer price_paise;

  @JsonProperty("price_paise")
  public Integer getPrice_paise() {
    return price_paise;
  }

  @JsonProperty("price_paise")
  public void setPrice_paise(Integer value) {
    this.price_paise = value;
    set("price_paise", value);
  }

  private String currency;

  @JsonProperty("currency")
  public String getCurrency() {
    return currency;
  }

  @JsonProperty("currency")
  public void setCurrency(String value) {
    this.currency = value;
    set("currency", value);
  }

  private String android_product_id;

  @JsonProperty("android_product_id")
  public String getAndroid_product_id() {
    return android_product_id;
  }

  @JsonProperty("android_product_id")
  public void setAndroid_product_id(String value) {
    this.android_product_id = value;
    set("android_product_id", value);
  }
}
