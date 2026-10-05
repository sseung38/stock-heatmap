package com.stockheatmap.core.model;

/** One daily closing price; date is yyyy-MM-dd in New York time. */
public record DailyBar(String date, double close) {}
