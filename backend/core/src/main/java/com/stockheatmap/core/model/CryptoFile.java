package com.stockheatmap.core.model;

import java.util.Map;

public record CryptoFile(String asOf, Map<String, CoinQuote> coins) {}
