package com.readiq.backend.api;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Probe responses reveal no database, credentials, or exception details. */
@RestController
public class OperationalHealthController {
  private final DataSource database;
  private final ApplicationAvailability availability;

  public OperationalHealthController(DataSource database, ApplicationAvailability availability) {
    this.database = database;
    this.availability = availability;
  }

  @GetMapping("/health/live")
  public ResponseEntity<Map<String, String>> live() {
    // Database outages must not cause a container restart loop.
    return result(availability.getLivenessState() == LivenessState.CORRECT);
  }

  @GetMapping("/health/ready")
  public ResponseEntity<Map<String, String>> ready() {
    if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) return result(false);
    try (var connection = database.getConnection(); var query = connection.createStatement()) {
      query.setQueryTimeout(2);
      // Check the shared request-protection schema and permission without reading user data.
      try (var ignored = query.executeQuery("select 1 from public.api_rate_limits where false")) {
        return result(true);
      }
    } catch (SQLException | RuntimeException failure) {
      return result(false);
    }
  }

  private ResponseEntity<Map<String, String>> result(boolean healthy) {
    return ResponseEntity.status(healthy ? 200 : 503).header("Cache-Control", "no-store")
        .body(Map.of("status", healthy ? "UP" : "DOWN"));
  }
}
