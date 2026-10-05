package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.stockheatmap.core.model.DailyBar;
import com.stockheatmap.core.model.StockBaseline;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselinesTest {

  /** Bars on the given dates with closes 100, 101, 102, ... */
  private static List<DailyBar> bars(String... dates) {
    List<DailyBar> bars = new ArrayList<>();
    for (int i = 0; i < dates.length; i++) {
      bars.add(new DailyBar(dates[i], 100 + i));
    }
    return bars;
  }

  private static String[] weekdays(String start, int count) {
    List<String> dates = new ArrayList<>();
    LocalDate d = LocalDate.parse(start);
    while (dates.size() < count) {
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
        dates.add(d.toString());
      }
      d = d.plusDays(1);
    }
    return dates.toArray(String[]::new);
  }

  @Test
  void keepsLast30ClosesAndLastCloseDate() {
    String[] dates = weekdays("2026-08-03", 40);

    StockBaseline b = Baselines.derive(bars(dates)).orElseThrow();

    assertThat(b.closes()).hasSize(30);
    assertThat(b.closes().getFirst()).isEqualTo(110.0);
    assertThat(b.closes().getLast()).isEqualTo(139.0);
    assertThat(b.lastCloseDate()).isEqualTo(dates[39]);
  }

  @Test
  void recordsLastCloseOfEachYear() {
    StockBaseline b =
        Baselines.derive(bars("2025-12-29", "2025-12-30", "2025-12-31", "2026-01-02", "2026-01-05"))
            .orElseThrow();

    assertThat(b.yearEndCloses()).containsExactly(entry("2025", 102.0), entry("2026", 104.0));
  }

  @Test
  void keepsShortHistoryAsIs() {
    StockBaseline b = Baselines.derive(bars("2026-10-01", "2026-10-02")).orElseThrow();

    assertThat(b.closes()).containsExactly(100.0, 101.0);
  }

  @Test
  void returnsEmptyForNoBars() {
    assertThat(Baselines.derive(List.of())).isEmpty();
  }

  @Test
  void barsStartAtEarlierOfDec20AndFiftyDaysAgo() {
    assertThat(Baselines.barsStartDate(Instant.parse("2026-10-04T21:00:00Z")))
        .isEqualTo(LocalDate.parse("2025-12-20"));
    assertThat(Baselines.barsStartDate(Instant.parse("2026-01-05T22:00:00Z")))
        .isEqualTo(LocalDate.parse("2025-11-16"));
  }
}
