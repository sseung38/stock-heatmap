package com.stockheatmap.jobs;

import com.stockheatmap.core.model.WatchlistItem;
import java.util.List;

/** JSON error body; {@code invalid} lists the watchlist items that were rejected (may be empty). */
public record ErrorBody(String error, List<WatchlistItem> invalid) {}
