package com.stockheatmap.core.model;

import java.util.List;
import java.util.Map;

/** Last 30 daily closes (oldest first), the date of the newest one, and each year's last close. */
public record StockBaseline(List<Double> closes, String lastCloseDate, Map<String, Double> yearEndCloses) {}
