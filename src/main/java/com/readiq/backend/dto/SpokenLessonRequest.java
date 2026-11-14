package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpokenLessonRequest(
    @NotBlank @Size(max = 160) String title,
    @NotBlank @Pattern(regexp = "conversation|pronunciation|speaking") String category,
    @Size(max = 280) String summary,
    @NotBlank @Size(max = 40000) String content,
    @Min(0) @Max(100000) Integer lesson_order,
    Boolean is_published) {}
