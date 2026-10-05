package com.stockheatmap.core.model;

/** Everything GET /market-data returns; a field is null until its file has been written once. */
public record MarketData(StocksFile stocks, CryptoFile crypto, BaselinesFile baselines, MetadataFile metadata) {}
