package com.stockheatmap.core.model;

public record StockQuote(double price, double prevClose, long volume, String tradingDay, String asOf) {}
