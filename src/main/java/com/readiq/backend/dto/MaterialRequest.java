package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class MaterialRequest extends Payload {
  private String material_type;

  @JsonProperty("material_type")
  public String getMaterial_type() {
    return material_type;
  }

  @JsonProperty("material_type")
  public void setMaterial_type(String value) {
    this.material_type = value;
    set("material_type", value);
  }

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

  private String year;

  @JsonProperty("year")
  public String getYear() {
    return year;
  }

  @JsonProperty("year")
  public void setYear(String value) {
    this.year = value;
    set("year", value);
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

  private String thumbnail_url;

  @JsonProperty("thumbnail_url")
  public String getThumbnail_url() {
    return thumbnail_url;
  }

  @JsonProperty("thumbnail_url")
  public void setThumbnail_url(String value) {
    this.thumbnail_url = value;
    set("thumbnail_url", value);
  }
}
