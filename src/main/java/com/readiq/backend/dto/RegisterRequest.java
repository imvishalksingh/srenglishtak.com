package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class RegisterRequest extends Payload {
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

  private String full_name;

  @JsonProperty("full_name")
  public String getFull_name() {
    return full_name;
  }

  @JsonProperty("full_name")
  public void setFull_name(String value) {
    this.full_name = value;
    set("full_name", value);
  }

  private String admin_secret;

  @JsonProperty("admin_secret")
  public String getAdmin_secret() {
    return admin_secret;
  }

  @JsonProperty("admin_secret")
  public void setAdmin_secret(String value) {
    this.admin_secret = value;
    set("admin_secret", value);
  }
}
