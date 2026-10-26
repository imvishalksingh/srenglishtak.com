package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class QuizRequest extends Payload {
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

  private Integer duration_minutes;

  @JsonProperty("duration_minutes")
  public Integer getDuration_minutes() {
    return duration_minutes;
  }

  @JsonProperty("duration_minutes")
  public void setDuration_minutes(Integer value) {
    this.duration_minutes = value;
    set("duration_minutes", value);
  }

  private String type;

  @JsonProperty("type")
  public String getType() {
    return type;
  }

  @JsonProperty("type")
  public void setType(String value) {
    this.type = value;
    set("type", value);
  }

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

  private List<QuestionRequest> questions;

  @JsonProperty("questions")
  public List<QuestionRequest> getQuestions() {
    return questions;
  }

  @JsonProperty("questions")
  public void setQuestions(List<QuestionRequest> value) {
    this.questions = value;
    set("questions", value);
  }
}
