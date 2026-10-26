package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "profiles")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class ProfilesEntity {
  @Id
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "email", columnDefinition = "text")
  @JsonProperty("email")
  public String email;

  @Column(name = "full_name", columnDefinition = "text")
  @JsonProperty("full_name")
  public String full_name;

  @Column(name = "avatar_url", columnDefinition = "text")
  @JsonProperty("avatar_url")
  public String avatar_url;

  @Column(name = "role", columnDefinition = "text")
  @JsonProperty("role")
  public String role;

  @Column(name = "class", columnDefinition = "text")
  @JsonProperty("class")
  public String studentClass;

  @Column(name = "learning_goal", columnDefinition = "text")
  @JsonProperty("learning_goal")
  public String learning_goal;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
