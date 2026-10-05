package com.stockheatmap.core.model;

import java.util.List;

public record SymbolsFile(String asOf, List<String> validStockSymbols, List<String> validCoinIds) {}
