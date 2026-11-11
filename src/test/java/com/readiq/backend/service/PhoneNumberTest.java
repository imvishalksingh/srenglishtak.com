package com.readiq.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import com.readiq.backend.api.ApiException;
import org.junit.jupiter.api.Test;

class PhoneNumberTest {
  @Test void normalizesIndianAndInternationalContactNumbers() {
    assertEquals("+919876543210",PhoneNumber.normalize("98765 43210"));
    assertEquals("+919876543210",PhoneNumber.normalize("+91 (98765) 43210"));
    assertEquals("+442079460958",PhoneNumber.normalize("+44 20 7946 0958"));
  }
  @Test void rejectsMissingInvalidAndOversizedNumbers() {
    for (String input:new String[]{"","123","1234567890","+910123456789","+91+9876543210","abc9876543210","+01234567890","+1234567890123456"})
      assertThrows(ApiException.class,()->PhoneNumber.normalize(input));
    assertThrows(ApiException.class,()->PhoneNumber.normalize(null));
    assertThrows(ApiException.class,()->PhoneNumber.normalize(9876543210L));
  }
}
