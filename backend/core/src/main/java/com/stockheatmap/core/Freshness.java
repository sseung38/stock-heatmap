package com.stockheatmap.core;

/** Spec §7: a refresh writes its file only if at least 90% of its universe is fresh. */
public final class Freshness {

  public static final double MIN_FRESH_RATIO = 0.9;

  private Freshness() {}

  public static void assertFresh(double ratio, String what) {
    if (ratio < MIN_FRESH_RATIO) {
      throw new StaleDataException(
          "%s: only %.1f%% fresh (need %.0f%%); keeping the previous file"
              .formatted(what, ratio * 100, MIN_FRESH_RATIO * 100));
    }
  }
}
