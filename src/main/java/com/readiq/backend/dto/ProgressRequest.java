package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
import java.util.*;

public final class ProgressRequest extends Payload {
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

  private Integer current_page;

  @JsonProperty("current_page")
  public Integer getCurrent_page() {
    return current_page;
  }

  @JsonProperty("current_page")
  public void setCurrent_page(Integer value) {
    this.current_page = value;
    set("current_page", value);
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

  private BigDecimal progress_percent;

  @JsonProperty("progress_percent")
  public BigDecimal getProgress_percent() {
    return progress_percent;
  }

  @JsonProperty("progress_percent")
  public void setProgress_percent(BigDecimal value) {
    this.progress_percent = value;
    set("progress_percent", value);
  }

  private Boolean is_completed;

  @JsonProperty("is_completed")
  public Boolean getIs_completed() {
    return is_completed;
  }

  @JsonProperty("is_completed")
  public void setIs_completed(Boolean value) {
    this.is_completed = value;
    set("is_completed", value);
  }

  private Integer total_minutes_read;

  @JsonProperty("total_minutes_read")
  public Integer getTotal_minutes_read() {
    return total_minutes_read;
  }

  @JsonProperty("total_minutes_read")
  public void setTotal_minutes_read(Integer value) {
    this.total_minutes_read = value;
    set("total_minutes_read", value);
  }

  private String last_position;

  @JsonProperty("last_position")
  public String getLast_position() {
    return last_position;
  }

  @JsonProperty("last_position")
  public void setLast_position(String value) {
    this.last_position = value;
    set("last_position", value);
  }
}
