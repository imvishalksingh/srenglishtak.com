package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class VocabularyRequest extends Payload {
  private String word;

  @JsonProperty("word")
  public String getWord() {
    return word;
  }

  @JsonProperty("word")
  public void setWord(String value) {
    this.word = value;
    set("word", value);
  }

  private String meaning;

  @JsonProperty("meaning")
  public String getMeaning() {
    return meaning;
  }

  @JsonProperty("meaning")
  public void setMeaning(String value) {
    this.meaning = value;
    set("meaning", value);
  }

  private String example_sentence;

  @JsonProperty("example_sentence")
  public String getExample_sentence() {
    return example_sentence;
  }

  @JsonProperty("example_sentence")
  public void setExample_sentence(String value) {
    this.example_sentence = value;
    set("example_sentence", value);
  }

  private String category;

  @JsonProperty("category")
  public String getCategory() {
    return category;
  }

  @JsonProperty("category")
  public void setCategory(String value) {
    this.category = value;
    set("category", value);
  }
}
