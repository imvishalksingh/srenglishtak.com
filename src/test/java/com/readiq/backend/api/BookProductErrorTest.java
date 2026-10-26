package com.readiq.backend.api;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;

class BookProductErrorTest {
  private final ErrorHandler handler = new ErrorHandler();

  @Test
  void concurrentProductIndexConflictsGetTheSameActionableResponse() {
    var cause = new ConstraintViolationException("duplicate", new SQLException("private details", "23505"),
        "insert into books", "uq_book_android_product");
    var response = handler.integrity(new DataIntegrityViolationException("write failed", cause));
    assertEquals(409, response.getStatusCode().value());
    assertEquals(ApiException.bookProductAlreadyAssigned().getMessage(), ((Map<?,?>)response.getBody()).get("error"));
    assertEquals(409, handler.duplicate(new DuplicateKeyException("write failed", cause)).getStatusCode().value());
  }

  @Test
  void unrelatedDatabaseConstraintsKeepTheirGenericMessage() {
    var cause = new ConstraintViolationException("private user data", new SQLException("sensitive details", "23503"),
        "insert into books", "books_uploaded_by_fkey");
    var response = handler.integrity(new DataIntegrityViolationException("write failed", cause));
    assertEquals(400, response.getStatusCode().value());
    assertEquals("Invalid data or referenced resource", ((Map<?,?>)response.getBody()).get("error"));
  }
}
