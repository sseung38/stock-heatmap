package com.stockheatmap.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockheatmap.core.model.CoinGeckoMarket;
import com.stockheatmap.core.model.CryptoFile;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.WatchlistItem.CryptoItem;
import com.stockheatmap.integrations.coinbase.CoinbaseClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.http.HttpStatusException;
import com.stockheatmap.integrations.store.MemoryDataStore;
import com.stockheatmap.integrations.watchlist.MemoryWatchlistRepo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RefreshCryptoJobTest {

  private final MemoryDataStore store = new MemoryDataStore(JsonMapper.builder().build());
  private final MemoryWatchlistRepo watchlist = new MemoryWatchlistRepo();
  private final CoinGeckoClient coingecko = mock(CoinGeckoClient.class);
  private final CoinbaseClient coinbase = mock(CoinbaseClient.class);
  private RefreshCryptoJob job;

  @BeforeEach
  void setUp() {
    job = new RefreshCryptoJob(store, watchlist, coingecko, coinbase,
        Clock.fixed(Instant.parse("2026-10-02T18:30:00Z"), ZoneOffset.UTC));
    when(coinbase.listUsdProducts()).thenReturn(Set.of("BTC-USD", "SOL-USD"));
  }

  private static CoinGeckoMarket market(String id, String symbol) {
    return new CoinGeckoMarket(id, symbol, id, 10.0, 1e9, 1.0, 2.0, 3.0, new CoinGeckoMarket.Sparkline(List.of(9.0, 10.0)));
  }

  @Test
  void writesTopCoinsAndSkipsSecondCallWhenWatchlistCoinIsAlreadyIncluded() {
    watchlist.put("u1", List.of(new CryptoItem("bitcoin", "BTC")));
    when(coingecko.topMarkets(50)).thenReturn(List.of(market("bitcoin", "btc"), market("binancecoin", "bnb")));

    RefreshSummary summary = job.run();

    CryptoFile written = store.get(DataKey.CRYPTO, CryptoFile.class).orElseThrow();
    assertThat(written.asOf()).isEqualTo("2026-10-02T18:30:00Z");
    assertThat(written.coins()).containsOnlyKeys("bitcoin", "binancecoin");
    assertThat(written.coins().get("bitcoin").coinbaseProductId()).isEqualTo("BTC-USD");
    assertThat(written.coins().get("binancecoin").coinbaseProductId()).isNull();
    assertThat(summary).isEqualTo(new RefreshSummary("crypto.json", 2, 1.0));
    verify(coingecko, never()).marketsByIds(anyList());
  }

  @Test
  void fetchesWatchlistCoinsOutsideTheTop50ById() {
    watchlist.put("u1", List.of(new CryptoItem("pepe", "PEPE")));
    when(coingecko.topMarkets(50)).thenReturn(List.of(market("bitcoin", "btc")));
    when(coingecko.marketsByIds(List.of("pepe"))).thenReturn(List.of(market("pepe", "pepe")));

    job.run();

    assertThat(store.get(DataKey.CRYPTO, CryptoFile.class).orElseThrow().coins()).containsKeys("bitcoin", "pepe");
  }

  @Test
  void writesNothingWhenTopMarketsCallFails() {
    when(coingecko.topMarkets(50)).thenThrow(new HttpStatusException(503, "api.coingecko.com/api/v3/coins/markets"));

    assertThatThrownBy(job::run).isInstanceOf(HttpStatusException.class);

    assertThat(store.raw(DataKey.CRYPTO)).isEmpty();
  }
}
