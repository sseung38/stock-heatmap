package com.stockheatmap.core.model;

import java.util.List;
import java.util.Map;

public record MetadataFile(String asOf, List<Sp500Entry> sp500, Map<String, Double> sharesOutstanding) {}
