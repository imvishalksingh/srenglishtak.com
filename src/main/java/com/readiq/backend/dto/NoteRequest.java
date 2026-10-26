package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class NoteRequest extends Payload {
  private UUID book_id;

  @JsonProperty("book_id")
  public UUID getBook_id() {
    return book_id;
  }

  @JsonProperty("book_id")
  public void setBook_id(UUID value) {
    this.book_id = value;
    set("book_id", value);
  }

  private Integer page;

  @JsonProperty("page")
  public Integer getPage() {
    return page;
  }

  @JsonProperty("page")
  public void setPage(Integer value) {
    this.page = value;
    set("page", value);
  }

  private String position;

  @JsonProperty("position")
  public String getPosition() {
    return position;
  }

  @JsonProperty("position")
  public void setPosition(String value) {
    this.position = value;
    set("position", value);
  }

  private String note_text;

  @JsonProperty("note_text")
  public String getNote_text() {
    return note_text;
  }

  @JsonProperty("note_text")
  public void setNote_text(String value) {
    this.note_text = value;
    set("note_text", value);
  }
}
