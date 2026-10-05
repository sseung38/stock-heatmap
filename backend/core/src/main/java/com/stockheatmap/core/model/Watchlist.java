package com.stockheatmap.core.model;

import java.util.List;

public record Watchlist(List<WatchlistItem> items, String updatedAt) {}
