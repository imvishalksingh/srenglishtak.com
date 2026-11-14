package com.readiq.backend.data.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "spoken_english_lessons")
@DynamicInsert
@DynamicUpdate
public class SpokenEnglishLessonsEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
  @Column(columnDefinition = "text") public String title;
  @Column(columnDefinition = "text") public String category;
  @Column(columnDefinition = "text") public String summary;
  @Column(columnDefinition = "text") public String content;
  public Integer lesson_order;
  public Boolean is_published;
  public OffsetDateTime created_at;
  public OffsetDateTime updated_at;
}
