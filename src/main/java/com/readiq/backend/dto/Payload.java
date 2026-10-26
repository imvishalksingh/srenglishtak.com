package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Payload {
  private final Map<String, Object> supplied = new LinkedHashMap<>();

  protected void set(String field, Object value) {
    supplied.put(field, value);
  }

  @JsonIgnore
  public final Map<String, Object> values() {
    return Collections.unmodifiableMap(supplied);
  }
}
