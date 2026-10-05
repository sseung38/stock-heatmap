package com.stockheatmap.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Merge {

  private Merge() {}

  /**
   * Combines this run's values with the previous file: every ticker in the universe gets its fresh
   * value, or else its previous value (which keeps its own older asOf). Tickers outside the
   * universe are dropped.
   *
   * @param previous values from the last written file, or null on the first run
   */
  public static <T> MergeResult<T> snapshot(Map<String, T> previous, Map<String, T> fresh, Collection<String> universe) {
    Map<String, T> merged = new LinkedHashMap<>();
    int freshCount = 0;
    for (String ticker : universe) {
      T value = fresh.get(ticker);
      if (value != null) {
        freshCount++;
      } else if (previous != null) {
        value = previous.get(ticker);
      }
      if (value != null) {
        merged.put(ticker, value);
      }
    }
    double ratio = universe.isEmpty() ? 0 : (double) freshCount / universe.size();
    return new MergeResult<>(merged, ratio);
  }
}
