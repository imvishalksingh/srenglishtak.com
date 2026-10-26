package com.readiq.backend.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "module_interests")
@DynamicInsert
@org.hibernate.annotations.DynamicUpdate
public class ModuleInterestsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  @JsonProperty("id")
  public UUID id;

  @Column(name = "user_id")
  @JsonProperty("user_id")
  public UUID user_id;

  @Column(name = "module_title", columnDefinition = "text")
  @JsonProperty("module_title")
  public String module_title;

  @Column(name = "is_notified")
  @JsonProperty("is_notified")
  public Boolean is_notified;

  @Column(name = "is_excited")
  @JsonProperty("is_excited")
  public Boolean is_excited;

  @Column(name = "created_at")
  @JsonProperty("created_at")
  public OffsetDateTime created_at;
}
