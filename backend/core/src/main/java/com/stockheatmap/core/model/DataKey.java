package com.stockheatmap.core.model;

/** Object keys in the data bucket (or file names in the local .data directory). */
public enum DataKey {
  STOCKS("stocks.json"),
  CRYPTO("crypto.json"),
  BASELINES("baselines.json"),
  METADATA("metadata.json"),
  SYMBOLS("symbols.json");

  private final String key;

  DataKey(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
