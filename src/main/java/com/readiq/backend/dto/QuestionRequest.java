package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class QuestionRequest extends Payload {
  private String question_text;

  @JsonProperty("question_text")
  public String getQuestion_text() {
    return question_text;
  }

  @JsonProperty("question_text")
  public void setQuestion_text(String value) {
    this.question_text = value;
    set("question_text", value);
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

  private String explanation;

  @JsonProperty("explanation")
  public String getExplanation() {
    return explanation;
  }

  @JsonProperty("explanation")
  public void setExplanation(String value) {
    this.explanation = value;
    set("explanation", value);
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

  private List<Object> options;

  @JsonProperty("options")
  public List<Object> getOptions() {
    return options;
  }

  @JsonProperty("options")
  public void setOptions(List<Object> value) {
    this.options = value;
    set("options", value);
  }

  private Integer correct_option_index;

  @JsonProperty("correct_option_index")
  public Integer getCorrect_option_index() {
    return correct_option_index;
  }

  @JsonProperty("correct_option_index")
  public void setCorrect_option_index(Integer value) {
    this.correct_option_index = value;
    set("correct_option_index", value);
  }
}
