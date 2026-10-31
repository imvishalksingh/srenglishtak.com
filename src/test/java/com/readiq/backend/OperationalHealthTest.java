package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.readiq.backend.api.OperationalHealthController;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.*;

class OperationalHealthTest {
  private final DataSource database = mock(DataSource.class);
  private final ApplicationAvailability availability = mock(ApplicationAvailability.class);
  private final OperationalHealthController health = new OperationalHealthController(database, availability);

  @Test void liveDoesNotDependOnDatabase() {
    when(availability.getLivenessState()).thenReturn(LivenessState.CORRECT);
    assertEquals(200, health.live().getStatusCode().value());
    verifyNoInteractions(database);
    when(availability.getLivenessState()).thenReturn(LivenessState.BROKEN);
    assertEquals(503, health.live().getStatusCode().value());
  }

  @Test void startupAndShutdownDoNotAcceptTraffic() {
    when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
    assertEquals(503, health.ready().getStatusCode().value());
    verifyNoInteractions(database);
  }

  @Test void readyChecksProtectionSchemaAndClosesResources() throws Exception {
    when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
    var connection = mock(Connection.class);
    var statement = mock(Statement.class);
    var rows = mock(ResultSet.class);
    when(database.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery("select 1 from public.api_rate_limits where false")).thenReturn(rows);
    assertEquals(200, health.ready().getStatusCode().value());
    verify(statement).setQueryTimeout(2);
    verify(rows).close(); verify(statement).close(); verify(connection).close();
  }

  @Test void databaseOutageIsSanitizedAndNeverCached() throws Exception {
    when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
    when(database.getConnection()).thenThrow(new SQLException("secret host and password"));
    var response = health.ready();
    assertEquals(503, response.getStatusCode().value());
    assertEquals(java.util.Map.of("status", "DOWN"), response.getBody());
    assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
  }
}
