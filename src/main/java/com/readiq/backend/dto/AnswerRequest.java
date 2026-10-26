package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class AnswerRequest extends Payload {
  private UUID question_id;

  @JsonProperty("question_id")
  public UUID getQuestion_id() {
    return question_id;
  }

  @JsonProperty("question_id")
  public void setQuestion_id(UUID value) {
    this.question_id = value;
    set("question_id", value);
  }

  private Integer selected_option_index;

  @JsonProperty("selected_option_index")
  public Integer getSelected_option_index() {
    return selected_option_index;
  }

  @JsonProperty("selected_option_index")
  public void setSelected_option_index(Integer value) {
    this.selected_option_index = value;
    set("selected_option_index", value);
  }
}
