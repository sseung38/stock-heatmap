package com.stockheatmap.core;

import com.stockheatmap.core.model.Sp500Entry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Reads Wikipedia's "List of S&P 500 companies" constituents table. */
public final class Sp500Parser {

  public static final List<String> GICS_SECTORS = List.of(
      "Communication Services", "Consumer Discretionary", "Consumer Staples", "Energy", "Financials",
      "Health Care", "Industrials", "Information Technology", "Materials", "Real Estate", "Utilities");

  static final int MIN_ROWS = 495;
  static final int MAX_ROWS = 510;

  private Sp500Parser() {}

  public static List<Sp500Entry> parse(String html) {
    Element table = Jsoup.parse(html).getElementById("constituents");
    if (table == null) {
      return List.of();
    }
    Elements rows = table.select("tr");
    if (rows.isEmpty()) {
      return List.of();
    }
    // Locate columns by header text so a reordered table still parses.
    List<String> headers = rows.getFirst().select("th").eachText();
    int symbolCol = headers.indexOf("Symbol");
    int nameCol = headers.indexOf("Security");
    int sectorCol = headers.indexOf("GICS Sector");
    if (symbolCol < 0 || nameCol < 0 || sectorCol < 0) {
      return List.of();
    }

    List<Sp500Entry> entries = new ArrayList<>();
    for (Element row : rows.subList(1, rows.size())) {
      Elements cells = row.select("td");
      if (cells.size() <= Math.max(symbolCol, Math.max(nameCol, sectorCol))) {
        continue;
      }
      entries.add(new Sp500Entry(
          cells.get(symbolCol).text().trim(), cells.get(nameCol).text().trim(), cells.get(sectorCol).text().trim()));
    }
    return entries;
  }

  /** Spec §7: accept only 495–510 rows that cover all 11 GICS sectors. */
  public static void validate(List<Sp500Entry> entries) {
    if (entries.size() < MIN_ROWS || entries.size() > MAX_ROWS) {
      throw new InvalidScrapeException(
          "Expected %d-%d S&P 500 rows, got %d".formatted(MIN_ROWS, MAX_ROWS, entries.size()));
    }
    Set<String> missing = new HashSet<>(GICS_SECTORS);
    entries.forEach(e -> missing.remove(e.sector()));
    if (!missing.isEmpty()) {
      throw new InvalidScrapeException("S&P 500 scrape is missing sectors: " + missing);
    }
  }
}
