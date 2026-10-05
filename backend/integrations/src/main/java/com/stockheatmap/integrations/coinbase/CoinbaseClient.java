package com.stockheatmap.integrations.coinbase;

import com.stockheatmap.integrations.http.RetryingHttp;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Coinbase Exchange public REST API (no key): which coins can be streamed live in the browser. */
public class CoinbaseClient {

  static final String PRODUCTS = "https://api.exchange.coinbase.com/products";

  private final RetryingHttp http;

  public CoinbaseClient(RetryingHttp http) {
    this.http = http;
  }

  /** Product ids like "BTC-USD" that are online, tradable and quoted in USD. */
  public Set<String> listUsdProducts() {
    JsonNode products = http.getJson(PRODUCTS, Map.of(), JsonNode.class);
    Set<String> ids = new HashSet<>();
    for (JsonNode p : products) {
      if ("USD".equals(p.path("quote_currency").asString())
          && "online".equals(p.path("status").asString())
          && !p.path("trading_disabled").asBoolean(false)) {
        ids.add(p.get("id").asString());
      }
    }
    return ids;
  }
}
