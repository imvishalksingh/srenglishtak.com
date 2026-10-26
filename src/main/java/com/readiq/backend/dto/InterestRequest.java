package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class InterestRequest extends Payload {
  private String moduleTitle;

  @JsonProperty("moduleTitle")
  public String getModuleTitle() {
    return moduleTitle;
  }

  @JsonProperty("moduleTitle")
  public void setModuleTitle(String value) {
    this.moduleTitle = value;
    set("moduleTitle", value);
  }

  private Boolean isNotified;

  @JsonProperty("isNotified")
  public Boolean getIsNotified() {
    return isNotified;
  }

  @JsonProperty("isNotified")
  public void setIsNotified(Boolean value) {
    this.isNotified = value;
    set("isNotified", value);
  }

  private Boolean isExcited;

  @JsonProperty("isExcited")
  public Boolean getIsExcited() {
    return isExcited;
  }

  @JsonProperty("isExcited")
  public void setIsExcited(Boolean value) {
    this.isExcited = value;
    set("isExcited", value);
  }
}
