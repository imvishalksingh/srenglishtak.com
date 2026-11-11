package com.readiq.backend.service;

import com.readiq.backend.api.ApiException;

/** Contact format validation only. This does not verify ownership by SMS/OTP. */
final class PhoneNumber {
  private PhoneNumber() {}

  static String normalize(Object value) {
    if (!(value instanceof String input)) throw ApiException.bad("Mobile number is required");
    input = input.strip();
    if (input.isEmpty() || input.length() > 32 || !input.matches("\\+?[0-9\\s()\\-]+"))
      throw ApiException.bad("Invalid mobile number");
    String number = input.replaceAll("[\\s()\\-]", "");
    if (number.matches("[6-9][0-9]{9}")) number = "+91" + number;
    boolean valid = number.startsWith("+91")
        ? number.matches("\\+91[6-9][0-9]{9}")
        : number.matches("\\+[1-9][0-9]{7,14}");
    if (!valid) throw ApiException.bad("Invalid mobile number");
    return number;
  }
}
