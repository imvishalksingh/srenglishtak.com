package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class ChallengeRequest extends Payload {
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

  private String prize_text;

  @JsonProperty("prize_text")
  public String getPrize_text() {
    return prize_text;
  }

  @JsonProperty("prize_text")
  public void setPrize_text(String value) {
    this.prize_text = value;
    set("prize_text", value);
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

  private OffsetDateTime start_time;

  @JsonProperty("start_time")
  public OffsetDateTime getStart_time() {
    return start_time;
  }

  @JsonProperty("start_time")
  public void setStart_time(OffsetDateTime value) {
    this.start_time = value;
    set("start_time", value);
  }

  private OffsetDateTime end_time;

  @JsonProperty("end_time")
  public OffsetDateTime getEnd_time() {
    return end_time;
  }

  @JsonProperty("end_time")
  public void setEnd_time(OffsetDateTime value) {
    this.end_time = value;
    set("end_time", value);
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
