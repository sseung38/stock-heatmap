package com.stockheatmap.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockheatmap.core.StaleDataException;
import com.stockheatmap.core.model.BaselinesFile;
import com.stockheatmap.core.model.CoinBaseline;
import com.stockheatmap.core.model.CoinQuote;
import com.stockheatmap.core.model.CryptoFile;
import com.stockheatmap.core.model.DailyBar;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.Sp500Entry;
import com.stockheatmap.core.model.StockBaseline;
import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.store.MemoryDataStore;
import com.stockheatmap.integrations.watchlist.MemoryWatchlistRepo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RefreshBaselinesJobTest {

  private static final Instant NOW = Instant.parse("2026-10-04T21:00:00Z");

  private final MemoryDataStore store = new MemoryDataStore(JsonMapper.builder().build());
  private final AlpacaClient alpaca = mock(AlpacaClient.class);
  private final CoinGeckoClient coingecko = mock(CoinGeckoClient.class);
  private final List<Long> sleeps = new ArrayList<>();
  private RefreshBaselinesJob job;

  @BeforeEach
  void setUp() {
    job = new RefreshBaselinesJob(store, new MemoryWatchlistRepo(), alpaca, coingecko,
        Clock.fixed(NOW, ZoneOffset.UTC), sleeps::add);
  }

  private void givenSp500(List<String> symbols) {
    store.put(DataKey.METADATA,
        new MetadataFile("", symbols.stream().map(s -> new Sp500Entry(s, s, "Energy")).toList(), Map.of()));
  }

  private static List<DailyBar> bars() {
    return List.of(new DailyBar("2025-12-31", 90), new DailyBar("2026-10-02", 100));
  }

  private static List<String> tickers(int n) {
    return IntStream.range(0, n).mapToObj(i -> "T" + i).toList();
  }

  private static CoinQuote coin(String symbol) {
    return new CoinQuote(symbol, symbol, 1, 0.0, 0.0, 0.0, 1, List.of(), null, "");
  }

  @Test
  void requestsBarsFromStartDateUntilTwentyMinutesAgo() {
    givenSp500(List.of("AAPL"));
    when(alpaca.getDailyBars(anyList(), any(), any())).thenReturn(Map.of("AAPL", bars()));

    job.run();

    verify(alpaca).getDailyBars(List.of("AAPL"), LocalDate.parse("2025-12-20"), Instant.parse("2026-10-04T20:40:00Z"));
    StockBaseline aapl = store.get(DataKey.BASELINES, BaselinesFile.class).orElseThrow().stocks().get("AAPL");
    assertThat(aapl.closes()).containsExactly(90.0, 100.0);
    assertThat(aapl.lastCloseDate()).isEqualTo("2026-10-02");
  }

  @Test
  void keepsPreviousBaselineForTickerWithoutBars() {
    List<String> universe = tickers(10);
    givenSp500(universe);
    StockBaseline old = new StockBaseline(List.of(1.0), "2026-09-30", Map.of("2025", 1.0));
    store.put(DataKey.BASELINES, new BaselinesFile("old", Map.of("T9", old), Map.of()));
    Map<String, List<DailyBar>> fresh = new HashMap<>();
    universe.subList(0, 9).forEach(s -> fresh.put(s, bars()));
    when(alpaca.getDailyBars(anyList(), any(), any())).thenReturn(fresh);

    job.run();

    assertThat(store.get(DataKey.BASELINES, BaselinesFile.class).orElseThrow().stocks().get("T9")).isEqualTo(old);
  }

  @Test
  void keepsPreviousFileWhenLessThanNinetyPercentFresh() {
    List<String> universe = tickers(10);
    givenSp500(universe);
    BaselinesFile previous = new BaselinesFile("old", Map.of(), Map.of());
    store.put(DataKey.BASELINES, previous);
    when(alpaca.getDailyBars(anyList(), any(), any())).thenReturn(Map.of("T0", bars()));

    assertThatThrownBy(job::run).isInstanceOf(StaleDataException.class);

    assertThat(store.get(DataKey.BASELINES, BaselinesFile.class)).contains(previous);
  }

  @Test
  void fetchesJanuaryFirstPriceOnlyForCoinsMissingThisYear() {
    givenSp500(List.of("AAPL"));
    when(alpaca.getDailyBars(anyList(), any(), any())).thenReturn(Map.of("AAPL", bars()));
    store.put(DataKey.CRYPTO, new CryptoFile("", Map.of("bitcoin", coin("BTC"), "solana", coin("SOL"), "ethereum", coin("ETH"))));
    store.put(DataKey.BASELINES, new BaselinesFile("old", Map.of(),
        Map.of("bitcoin", new CoinBaseline(93500, 2026), "ethereum", new CoinBaseline(3000, 2025))));
    when(coingecko.priceOn(eq("solana"), any())).thenReturn(OptionalDouble.of(150));
    when(coingecko.priceOn(eq("ethereum"), any())).thenReturn(OptionalDouble.of(3300));

    job.run();

    Map<String, CoinBaseline> crypto = store.get(DataKey.BASELINES, BaselinesFile.class).orElseThrow().crypto();
    assertThat(crypto).containsEntry("bitcoin", new CoinBaseline(93500, 2026))
        .containsEntry("solana", new CoinBaseline(150, 2026))
        .containsEntry("ethereum", new CoinBaseline(3300, 2026));
    verify(coingecko, never()).priceOn(eq("bitcoin"), any());
    verify(coingecko).priceOn("solana", LocalDate.parse("2026-01-01"));
    assertThat(sleeps).containsExactly(2500L); // paced between the two CoinGecko calls
  }

  @Test
  void omitsCoinWithoutJanuaryFirstPrice() {
    givenSp500(List.of("AAPL"));
    when(alpaca.getDailyBars(anyList(), any(), any())).thenReturn(Map.of("AAPL", bars()));
    store.put(DataKey.CRYPTO, new CryptoFile("", Map.of("newcoin", coin("NEW"))));
    when(coingecko.priceOn(eq("newcoin"), any())).thenReturn(OptionalDouble.empty());

    job.run();

    assertThat(store.get(DataKey.BASELINES, BaselinesFile.class).orElseThrow().crypto()).isEmpty();
  }
}
