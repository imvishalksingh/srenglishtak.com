package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OnboardingRequest(
    @NotBlank @Size(max=100) String full_name,
    @NotBlank @Size(max=32) String phone_number,
    @JsonProperty("class") @NotBlank @Size(max=120) String studentClass,
    @NotBlank @Size(max=500) String learning_goal) {}
