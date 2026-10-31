package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import com.readiq.backend.config.NorthflankRuntimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class NorthflankRuntimeConfigurationTest {
  private void check(MockEnvironment env) {
    NorthflankRuntimeConfiguration.checkRuntimeDatabaseUrl(env)
        .postProcessBeanFactory(new DefaultListableBeanFactory());
  }

  @Test void rejectsMissingUnresolvedQuotedOrNonJdbcRuntimeValues() {
    assertThrows(IllegalStateException.class, () -> check(new MockEnvironment()));
    for (String value : java.util.List.of("${DB_URL}", "", "postgresql://db.example/postgres",
        "\"jdbc:postgresql://db.example/postgres\"")) {
      var failure = assertThrows(IllegalStateException.class,
          () -> check(new MockEnvironment().withProperty("spring.datasource.url", value)));
      assertNull(failure.getCause());
      assertTrue(failure.getMessage().contains("runtime environment"));
    }
  }

  @Test void preventsCredentialBearingUrlFromReachingDatasourceLogs() {
    for (String value : java.util.List.of("jdbc:postgresql://db.example/postgres?password=private-value",
        "jdbc:postgresql://db.example/postgres?sslmode=require&user=private-value",
        "jdbc:postgresql://user:private-value@db.example/postgres")) {
      var failure = assertThrows(IllegalStateException.class,
          () -> check(new MockEnvironment().withProperty("spring.datasource.url", value)));
      assertFalse(failure.getMessage().contains("private-value")); assertNull(failure.getCause());
    }
  }

  @Test void acceptsCredentialFreeJdbcUrlWithSeparateRuntimeCredentials() {
    assertDoesNotThrow(() -> check(new MockEnvironment()
        .withProperty("spring.datasource.url", "jdbc:postgresql://db.example:5432/postgres?sslmode=verify-full")
        .withProperty("spring.datasource.username", "fixture-user")
        .withProperty("spring.datasource.password", "private-value")));
  }
}
