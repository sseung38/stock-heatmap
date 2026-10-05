package com.stockheatmap.core.model;

import java.util.Map;

public record StocksFile(String asOf, Market market, Map<String, StockQuote> quotes) {}
