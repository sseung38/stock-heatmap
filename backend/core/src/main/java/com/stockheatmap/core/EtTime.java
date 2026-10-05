package com.stockheatmap.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** US market time helpers. Data is stored in UTC; trading days and display times are New York time. */
public final class EtTime {

  public static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

  private EtTime() {}

  /** The New York calendar date ("yyyy-MM-dd") of an ISO-8601 instant. */
  public static String etDate(String iso) {
    return etDate(Instant.parse(iso));
  }

  public static String etDate(Instant instant) {
    return instant.atZone(NEW_YORK).toLocalDate().toString();
  }

  /** "HH:MM ET", 24-hour clock. */
  public static String formatEtTime(String iso) {
    return CLOCK.format(Instant.parse(iso).atZone(NEW_YORK)) + " ET";
  }
}
