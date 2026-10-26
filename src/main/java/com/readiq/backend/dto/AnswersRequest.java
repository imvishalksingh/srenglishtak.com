package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class AnswersRequest extends Payload {
  private List<AnswerRequest> answers;

  @JsonProperty("answers")
  public List<AnswerRequest> getAnswers() {
    return answers;
  }

  @JsonProperty("answers")
  public void setAnswers(List<AnswerRequest> value) {
    this.answers = value;
    set("answers", value);
  }

  private Integer score;

  @JsonProperty("score")
  public Integer getScore() {
    return score;
  }

  @JsonProperty("score")
  public void setScore(Integer value) {
    this.score = value;
    set("score", value);
  }

  private Integer time_taken_ms;

  @JsonProperty("time_taken_ms")
  public Integer getTime_taken_ms() {
    return time_taken_ms;
  }

  @JsonProperty("time_taken_ms")
  public void setTime_taken_ms(Integer value) {
    this.time_taken_ms = value;
    set("time_taken_ms", value);
  }
}
