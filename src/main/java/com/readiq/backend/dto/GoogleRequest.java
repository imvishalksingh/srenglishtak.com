package com.readiq.backend.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

public final class GoogleRequest extends Payload {
  private String id_token;

  @JsonProperty("id_token")
  public String getId_token() {
    return id_token;
  }

  @JsonProperty("id_token")
  public void setId_token(String value) {
    this.id_token = value;
    set("id_token", value);
  }

  private String nonce;

  @JsonProperty("nonce")
  public String getNonce() {
    return nonce;
  }

  @JsonProperty("nonce")
  public void setNonce(String value) {
    this.nonce = value;
    set("nonce", value);
  }
}
