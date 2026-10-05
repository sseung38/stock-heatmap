package com.stockheatmap.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** One row of CoinGecko's /coins/markets response; field names match the API (percent values, not fractions). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinGeckoMarket(
    String id,
    String symbol,
    String name,
    double current_price,
    double market_cap,
    Double price_change_percentage_24h_in_currency,
    Double price_change_percentage_7d_in_currency,
    Double price_change_percentage_30d_in_currency,
    Sparkline sparkline_in_7d) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Sparkline(List<Double> price) {}
}
