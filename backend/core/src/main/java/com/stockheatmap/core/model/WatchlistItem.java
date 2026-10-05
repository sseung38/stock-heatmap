package com.stockheatmap.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** A watchlist entry; serialized with a "type" field of "stock" or "crypto". */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = WatchlistItem.StockItem.class, name = "stock"),
  @JsonSubTypes.Type(value = WatchlistItem.CryptoItem.class, name = "crypto")
})
public sealed interface WatchlistItem {

  record StockItem(String symbol) implements WatchlistItem {}

  record CryptoItem(String id, String symbol) implements WatchlistItem {}
}
