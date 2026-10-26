package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class SessionRequest extends Payload {
  private UUID book_id;

  @JsonProperty("book_id")
  public UUID getBook_id() {
    return book_id;
  }

  @JsonProperty("book_id")
  public void setBook_id(UUID value) {
    this.book_id = value;
    set("book_id", value);
  }

  private Integer start_page;

  @JsonProperty("start_page")
  public Integer getStart_page() {
    return start_page;
  }

  @JsonProperty("start_page")
  public void setStart_page(Integer value) {
    this.start_page = value;
    set("start_page", value);
  }

  private String device_type;

  @JsonProperty("device_type")
  public String getDevice_type() {
    return device_type;
  }

  @JsonProperty("device_type")
  public void setDevice_type(String value) {
    this.device_type = value;
    set("device_type", value);
  }

  private String source;

  @JsonProperty("source")
  public String getSource() {
    return source;
  }

  @JsonProperty("source")
  public void setSource(String value) {
    this.source = value;
    set("source", value);
  }
}
