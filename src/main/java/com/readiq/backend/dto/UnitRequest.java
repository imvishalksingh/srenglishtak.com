package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class UnitRequest extends Payload {
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

  private Integer unit_order;

  @JsonProperty("unit_order")
  public Integer getUnit_order() {
    return unit_order;
  }

  @JsonProperty("unit_order")
  public void setUnit_order(Integer value) {
    this.unit_order = value;
    set("unit_order", value);
  }

  private String image_url;

  @JsonProperty("image_url")
  public String getImage_url() {
    return image_url;
  }

  @JsonProperty("image_url")
  public void setImage_url(String value) {
    this.image_url = value;
    set("image_url", value);
  }
}
