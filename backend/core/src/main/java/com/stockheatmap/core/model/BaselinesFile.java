package com.stockheatmap.core.model;

import java.util.Map;

public record BaselinesFile(String asOf, Map<String, StockBaseline> stocks, Map<String, CoinBaseline> crypto) {}
