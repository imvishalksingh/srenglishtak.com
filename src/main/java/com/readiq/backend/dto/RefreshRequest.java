package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class RefreshRequest extends Payload {
  private String refresh_token;

  @JsonProperty("refresh_token")
  public String getRefresh_token() {
    return refresh_token;
  }

  @JsonProperty("refresh_token")
  public void setRefresh_token(String value) {
    this.refresh_token = value;
    set("refresh_token", value);
  }
}
