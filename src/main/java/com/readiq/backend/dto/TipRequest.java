package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class TipRequest extends Payload {
  private String title;

  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  @JsonProperty("title")
  public void setTitle(String value) {
    this.title = value;
    set("title", value);
  }

  private String content;

  @JsonProperty("content")
  public String getContent() {
    return content;
  }

  @JsonProperty("content")
  public void setContent(String value) {
    this.content = value;
    set("content", value);
  }

  private String author;

  @JsonProperty("author")
  public String getAuthor() {
    return author;
  }

  @JsonProperty("author")
  public void setAuthor(String value) {
    this.author = value;
    set("author", value);
  }

  private String image_url;

  @JsonProperty("image_url")
  public String getImage_url() {
    return image_url;
  }

  @JsonProperty("image_url")
  public void setImage_url(String value) {
    this.image_url = value;
    set("image_url", value);
  }
}
