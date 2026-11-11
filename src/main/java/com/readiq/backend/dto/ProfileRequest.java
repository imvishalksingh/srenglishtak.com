package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class ProfileRequest extends Payload {
  private String phone_number;
  @JsonProperty("phone_number")
  public String getPhone_number() { return phone_number; }
  @JsonProperty("phone_number")
  public void setPhone_number(String value) {
    this.phone_number=value;
    set("phone_number",value);
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

  private String avatar_url;

  @JsonProperty("avatar_url")
  public String getAvatar_url() {
    return avatar_url;
  }

  @JsonProperty("avatar_url")
  public void setAvatar_url(String value) {
    this.avatar_url = value;
    set("avatar_url", value);
  }

  private String studentClass;

  @JsonProperty("class")
  public String getStudentClass() {
    return studentClass;
  }

  @JsonProperty("class")
  public void setStudentClass(String value) {
    this.studentClass = value;
    set("class", value);
  }

  private String learning_goal;

  @JsonProperty("learning_goal")
  public String getLearning_goal() {
    return learning_goal;
  }

  @JsonProperty("learning_goal")
  public void setLearning_goal(String value) {
    this.learning_goal = value;
    set("learning_goal", value);
  }
}
