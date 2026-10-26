package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class LoginRequest extends Payload {
  private String email;

  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  @JsonProperty("email")
  public void setEmail(String value) {
    this.email = value;
    set("email", value);
  }

  private String password;

  @JsonProperty("password")
  public String getPassword() {
    return password;
  }

  @JsonProperty("password")
  public void setPassword(String value) {
    this.password = value;
    set("password", value);
  }
}
