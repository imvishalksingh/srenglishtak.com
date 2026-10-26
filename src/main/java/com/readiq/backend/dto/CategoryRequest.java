package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class CategoryRequest extends Payload {
  private String name;

  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String value) {
    this.name = value;
    set("name", value);
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

  private String icon_url;

  @JsonProperty("icon_url")
  public String getIcon_url() {
    return icon_url;
  }

  @JsonProperty("icon_url")
  public void setIcon_url(String value) {
    this.icon_url = value;
    set("icon_url", value);
  }

  private Integer order_index;

  @JsonProperty("order_index")
  public Integer getOrder_index() {
    return order_index;
  }

  @JsonProperty("order_index")
  public void setOrder_index(Integer value) {
    this.order_index = value;
    set("order_index", value);
  }
}
