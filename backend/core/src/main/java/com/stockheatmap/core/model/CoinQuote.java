package com.stockheatmap.core.model;

import java.util.List;

/** Percent changes are fractions (0.025 = 2.5%) and may be null when CoinGecko has no value. */
public record CoinQuote(
    String symbol,
    String name,
    double price,
    Double change24h,
    Double change7d,
    Double change30d,
    double marketCap,
    List<Double> sparkline7d,
    String coinbaseProductId,
    String asOf) {}
