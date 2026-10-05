package com.stockheatmap.core;

import com.stockheatmap.core.model.CoinGeckoMarket;
import com.stockheatmap.core.model.CoinQuote;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CryptoMapping {

  private CryptoMapping() {}

  /**
   * @param usdProducts Coinbase product ids that are online (e.g. "BTC-USD"); a coin without one is
   *     shown with its cached CoinGecko price and labelled "delayed"
   */
  public static CoinQuote toCoinQuote(CoinGeckoMarket m, Set<String> usdProducts, String now) {
    String symbol = m.symbol().toUpperCase(Locale.ROOT);
    String productId = symbol + "-USD";
    List<Double> sparkline = m.sparkline_in_7d() == null || m.sparkline_in_7d().price() == null
        ? List.of() : m.sparkline_in_7d().price();
    return new CoinQuote(
        symbol,
        m.name(),
        m.current_price(),
        fraction(m.price_change_percentage_24h_in_currency()),
        fraction(m.price_change_percentage_7d_in_currency()),
        fraction(m.price_change_percentage_30d_in_currency()),
        m.market_cap(),
        sparkline,
        usdProducts.contains(productId) ? productId : null,
        now);
  }

  private static Double fraction(Double percent) {
    return percent == null ? null : percent / 100;
  }
}
