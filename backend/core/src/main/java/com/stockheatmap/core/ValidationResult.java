package com.stockheatmap.core;

import com.stockheatmap.core.model.WatchlistItem;
import java.util.List;

/** Outcome of validating a PUT /watchlist body. */
public sealed interface ValidationResult {

  /** @param items the normalized items to save */
  record Valid(List<WatchlistItem> items) implements ValidationResult {}

  /** @param invalid the offending items (normalized); empty when the body itself is malformed */
  record Invalid(String error, List<WatchlistItem> invalid) implements ValidationResult {}
}
