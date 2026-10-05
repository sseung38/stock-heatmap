package com.stockheatmap.core;

import java.util.Map;

/** @param freshRatio share of the universe that came from this run (0..1) */
public record MergeResult<T>(Map<String, T> merged, double freshRatio) {}
