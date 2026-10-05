package com.stockheatmap.core;

import com.stockheatmap.core.model.DailyBar;
import com.stockheatmap.core.model.StockBaseline;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Turns daily bars into what the browser needs to compute 1W / 1M / YTD changes.
 * The browser picks the right close relative to each quote's trading day (spec §14.3).
 */
public final class Baselines {

  static final int CLOSES_KEPT = 30;

  private Baselines() {}

  /** @param barsAscending daily bars, oldest first */
  public static Optional<StockBaseline> derive(List<DailyBar> barsAscending) {
    if (barsAscending.isEmpty()) {
      return Optional.empty();
    }
    List<Double> closes =
        barsAscending.subList(Math.max(0, barsAscending.size() - CLOSES_KEPT), barsAscending.size()).stream()
            .map(DailyBar::close)
            .toList();
    Map<String, Double> yearEndCloses = new TreeMap<>();
    for (DailyBar bar : barsAscending) {
      yearEndCloses.put(bar.date().substring(0, 4), bar.close()); // later bars overwrite earlier ones
    }
    String lastCloseDate = barsAscending.getLast().date();
    return Optional.of(new StockBaseline(closes, lastCloseDate, yearEndCloses));
  }

  /**
   * First date to request bars from: early enough for 30 sessions and for last year's final close.
   * That is the earlier of Dec 20 of the previous year and 50 days ago (both in New York time).
   */
  public static LocalDate barsStartDate(Instant now) {
    LocalDate today = now.atZone(EtTime.NEW_YORK).toLocalDate();
    LocalDate dec20LastYear = LocalDate.of(today.getYear() - 1, 12, 20);
    LocalDate fiftyDaysAgo = today.minusDays(50);
    return dec20LastYear.isBefore(fiftyDaysAgo) ? dec20LastYear : fiftyDaysAgo;
  }
}
