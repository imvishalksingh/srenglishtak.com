package com.readiq.backend.service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Calendar-week activity, independent of device timezone and duplicate events. */
public final class WeeklyLearningGoal {
  private WeeklyLearningGoal() {}

  public static LocalDate weekStart(Instant now, ZoneId zone) {
    return now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  public static Map<String, Object> summarize(Instant now, ZoneId zone, Collection<Instant> activity) {
    LocalDate start = weekStart(now, zone);
    Instant from = start.atStartOfDay(zone).toInstant();
    Instant until = start.plusDays(7).atStartOfDay(zone).toInstant();
    var dates = new TreeSet<String>();
    for (Instant event : activity) {
      if (event != null && !event.isBefore(from) && event.isBefore(until) && !event.isAfter(now))
        dates.add(event.atZone(zone).toLocalDate().toString());
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("week_start", start.toString());
    result.put("starts_at", from.toString());
    result.put("ends_at", until.toString());
    result.put("time_zone", zone.getId());
    result.put("target_days", 7);
    result.put("active_dates", List.copyOf(dates));
    return result;
  }
}
