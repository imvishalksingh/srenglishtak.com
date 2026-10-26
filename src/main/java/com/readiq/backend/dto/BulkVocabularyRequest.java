package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class BulkVocabularyRequest extends Payload {
  private List<VocabularyRequest> items;

  @JsonProperty("items")
  public List<VocabularyRequest> getItems() {
    return items;
  }

  @JsonProperty("items")
  public void setItems(List<VocabularyRequest> value) {
    this.items = value;
    set("items", value);
  }
}
