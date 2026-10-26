package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class FinishSessionRequest extends Payload {
  private Integer end_page;

  @JsonProperty("end_page")
  public Integer getEnd_page() {
    return end_page;
  }

  @JsonProperty("end_page")
  public void setEnd_page(Integer value) {
    this.end_page = value;
    set("end_page", value);
  }

  private Integer pages_read;

  @JsonProperty("pages_read")
  public Integer getPages_read() {
    return pages_read;
  }

  @JsonProperty("pages_read")
  public void setPages_read(Integer value) {
    this.pages_read = value;
    set("pages_read", value);
  }

  private Integer minutes_spent;

  @JsonProperty("minutes_spent")
  public Integer getMinutes_spent() {
    return minutes_spent;
  }

  @JsonProperty("minutes_spent")
  public void setMinutes_spent(Integer value) {
    this.minutes_spent = value;
    set("minutes_spent", value);
  }

  private Boolean is_completed;

  @JsonProperty("is_completed")
  public Boolean getIs_completed() {
    return is_completed;
  }

  @JsonProperty("is_completed")
  public void setIs_completed(Boolean value) {
    this.is_completed = value;
    set("is_completed", value);
  }
}
