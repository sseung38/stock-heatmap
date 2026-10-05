package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.stockheatmap.core.model.CoinGeckoMarket;
import com.stockheatmap.core.model.CoinQuote;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CryptoMappingTest {

  private static CoinGeckoMarket market(String id, String symbol, Double pct30d) {
    return new CoinGeckoMarket(id, symbol, id.toUpperCase(), 100.0, 5.0e9, 2.5, -10.0, pct30d,
        new CoinGeckoMarket.Sparkline(List.of(90.0, 95.0, 100.0)));
  }

  @Test
  void convertsPercentagesToFractionsAndLinksCoinbaseProduct() {
    CoinQuote q = CryptoMapping.toCoinQuote(market("bitcoin", "btc", 20.0), Set.of("BTC-USD"), "2026-10-04T00:00:00Z");

    assertThat(q.symbol()).isEqualTo("BTC");
    assertThat(q.change24h()).isCloseTo(0.025, within(1e-12));
    assertThat(q.change7d()).isCloseTo(-0.10, within(1e-12));
    assertThat(q.change30d()).isCloseTo(0.20, within(1e-12));
    assertThat(q.marketCap()).isEqualTo(5.0e9);
    assertThat(q.sparkline7d()).containsExactly(90.0, 95.0, 100.0);
    assertThat(q.coinbaseProductId()).isEqualTo("BTC-USD");
    assertThat(q.asOf()).isEqualTo("2026-10-04T00:00:00Z");
  }

  @Test
  void leavesProductNullWhenCoinbaseDoesNotListTheCoin() {
    assertThat(CryptoMapping.toCoinQuote(market("binancecoin", "bnb", 1.0), Set.of("BTC-USD"), "x").coinbaseProductId())
        .isNull();
  }

  @Test
  void keepsMissingPercentChangeNull() {
    assertThat(CryptoMapping.toCoinQuote(market("newcoin", "new", null), Set.of(), "x").change30d()).isNull();
  }
}
