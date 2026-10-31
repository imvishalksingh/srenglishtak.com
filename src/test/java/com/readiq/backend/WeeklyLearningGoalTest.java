package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import com.readiq.backend.service.WeeklyLearningGoal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeeklyLearningGoalTest {
  final ZoneId india = ZoneId.of("Asia/Kolkata");
  @Test void combinesAndDeduplicatesLearningEventsWithinTheCurrentWeek() {
    var now = Instant.parse("2026-10-30T10:00:00Z");
    var result = WeeklyLearningGoal.summarize(now, india, List.of(
      Instant.parse("2026-10-26T06:00:00Z"), Instant.parse("2026-10-26T09:00:00Z"),
      Instant.parse("2026-10-29T06:00:00Z"), Instant.parse("2026-10-25T10:00:00Z"),
      Instant.parse("2026-10-31T10:00:00Z")));
    assertEquals("2026-10-26", result.get("week_start"));
    assertEquals(List.of("2026-10-26", "2026-10-29"), result.get("active_dates"));
    assertEquals("2026-10-25T18:30:00Z", result.get("starts_at"));
    assertEquals("2026-11-01T18:30:00Z", result.get("ends_at"));
  }
  @Test void resetsAtMondayMidnightInTheConfiguredTimezone() {
    var now = Instant.parse("2026-11-01T18:30:00Z");
    var result = WeeklyLearningGoal.summarize(now, india, List.of(now.minusSeconds(1), now));
    assertEquals("2026-11-02", result.get("week_start"));
    assertEquals(List.of("2026-11-02"), result.get("active_dates"));
  }
  @Test void handlesYearBoundariesAndEmptyNewAccounts() {
    var result = WeeklyLearningGoal.summarize(Instant.parse("2027-01-01T10:00:00Z"), india, List.of());
    assertEquals("2026-12-28", result.get("week_start"));
    assertEquals(List.of(), result.get("active_dates"));
  }
  @Test void acceptsAllSevenDaysWithoutCountingEventsTwice() {
    var events = new ArrayList<Instant>();
    for (int day = 26; day <= 31; day++) events.add(Instant.parse("2026-10-" + day + "T10:00:00Z"));
    events.add(Instant.parse("2026-11-01T10:00:00Z"));
    var result = WeeklyLearningGoal.summarize(Instant.parse("2026-11-01T12:00:00Z"), india, events);
    assertEquals(7, ((List<?>)result.get("active_dates")).size());
  }
  @Test void respectsDaylightSavingCalendarWeeks() {
    var result = WeeklyLearningGoal.summarize(Instant.parse("2026-03-08T18:00:00Z"), ZoneId.of("America/New_York"), List.of());
    assertEquals(167, Duration.between(Instant.parse((String)result.get("starts_at")), Instant.parse((String)result.get("ends_at"))).toHours());
  }
}
