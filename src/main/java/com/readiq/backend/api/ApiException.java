package com.readiq.backend.api;

public class ApiException extends RuntimeException {
  private final int status;

  public ApiException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static ApiException bad(String message) {
    return new ApiException(400, message);
  }

  public static ApiException bookProductAlreadyAssigned() {
    return new ApiException(409,
        "This Google Play product ID is already assigned to another book. Use a different product ID for each book");
  }

  public static ApiException missing() {
    return new ApiException(404, "Resource not found");
  }
}
