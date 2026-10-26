package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class LessonRequest extends Payload {
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

  private String content_type;

  @JsonProperty("content_type")
  public String getContent_type() {
    return content_type;
  }

  @JsonProperty("content_type")
  public void setContent_type(String value) {
    this.content_type = value;
    set("content_type", value);
  }

  private String content_data;

  @JsonProperty("content_data")
  public String getContent_data() {
    return content_data;
  }

  @JsonProperty("content_data")
  public void setContent_data(String value) {
    this.content_data = value;
    set("content_data", value);
  }

  private Integer lesson_order;

  @JsonProperty("lesson_order")
  public Integer getLesson_order() {
    return lesson_order;
  }

  @JsonProperty("lesson_order")
  public void setLesson_order(Integer value) {
    this.lesson_order = value;
    set("lesson_order", value);
  }
}
