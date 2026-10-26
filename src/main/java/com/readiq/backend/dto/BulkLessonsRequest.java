package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class BulkLessonsRequest extends Payload {
  private List<LessonRequest> lessons;

  @JsonProperty("lessons")
  public List<LessonRequest> getLessons() {
    return lessons;
  }

  @JsonProperty("lessons")
  public void setLessons(List<LessonRequest> value) {
    this.lessons = value;
    set("lessons", value);
  }
}
