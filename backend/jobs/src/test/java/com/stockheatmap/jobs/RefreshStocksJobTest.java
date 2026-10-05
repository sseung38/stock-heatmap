package com.stockheatmap.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stockheatmap.core.StaleDataException;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.Market;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.Sp500Entry;
import com.stockheatmap.core.model.StockQuote;
import com.stockheatmap.core.model.StocksFile;
import com.stockheatmap.core.model.WatchlistItem.StockItem;
import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.alpaca.MarketClock;
import com.stockheatmap.integrations.store.MemoryDataStore;
import com.stockheatmap.integrations.watchlist.MemoryWatchlistRepo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RefreshStocksJobTest {

  private static final Instant NOW = Instant.parse("2026-10-02T18:35:00Z");

  private final MemoryDataStore store = new MemoryDataStore(JsonMapper.builder().build());
  private final MemoryWatchlistRepo watchlist = new MemoryWatchlistRepo();
  private final AlpacaClient alpaca = mock(AlpacaClient.class);
  private RefreshStocksJob job;

  @BeforeEach
  void setUp() {
    job = new RefreshStocksJob(store, watchlist, alpaca, Clock.fixed(NOW, ZoneOffset.UTC));
    when(alpaca.getClock()).thenReturn(new MarketClock(true));
  }

  private static StockQuote quote(double price) {
    return new StockQuote(price, 100, 1000, "2026-10-02", "2026-10-02T18:20:00Z");
  }

  private static MetadataFile metadata(List<String> symbols) {
    return new MetadataFile("", symbols.stream().map(s -> new Sp500Entry(s, s, "Financials")).toList(), Map.of());
  }

  private static List<String> tickers(int n) {
    return IntStream.range(0, n).mapToObj(i -> "T" + i).toList();
  }

  private static Map<String, StockQuote> quotesFor(List<String> symbols) {
    Map<String, StockQuote> quotes = new LinkedHashMap<>();
    symbols.forEach(s -> quotes.put(s, quote(101)));
    return quotes;
  }

  @Test
  void writesStocksFileForSp500PlusWatchlist() {
    store.put(DataKey.METADATA, metadata(List.of("AAPL", "MSFT")));
    watchlist.put("u1", List.of(new StockItem("PLTR")));
    when(alpaca.getSnapshots(List.of("AAPL", "MSFT", "PLTR"))).thenReturn(quotesFor(List.of("AAPL", "MSFT", "PLTR")));

    RefreshSummary summary = job.run();

    StocksFile written = store.get(DataKey.STOCKS, StocksFile.class).orElseThrow();
    assertThat(written.asOf()).isEqualTo("2026-10-02T18:35:00Z");
    assertThat(written.market()).isEqualTo(new Market(true));
    assertThat(written.quotes()).containsOnlyKeys("AAPL", "MSFT", "PLTR");
    assertThat(summary).isEqualTo(new RefreshSummary("stocks.json", 3, 1.0));
  }

  @Test
  void keepsPreviousFileWhenLessThanNinetyPercentFresh() {
    List<String> universe = tickers(10);
    store.put(DataKey.METADATA, metadata(universe));
    StocksFile previous = new StocksFile("2026-10-02T18:30:00Z", new Market(true), quotesFor(universe));
    store.put(DataKey.STOCKS, previous);
    when(alpaca.getSnapshots(anyList())).thenReturn(quotesFor(universe.subList(0, 8)));

    assertThatThrownBy(job::run).isInstanceOf(StaleDataException.class);

    assertThat(store.get(DataKey.STOCKS, StocksFile.class)).contains(previous);
  }

  @Test
  void carriesPreviousQuoteForTickerMissingThisRun() {
    List<String> universe = tickers(10);
    store.put(DataKey.METADATA, metadata(universe));
    store.put(DataKey.STOCKS, new StocksFile("old", new Market(true), Map.of("T9", quote(50))));
    when(alpaca.getSnapshots(anyList())).thenReturn(quotesFor(universe.subList(0, 9)));

    job.run();

    assertThat(store.get(DataKey.STOCKS, StocksFile.class).orElseThrow().quotes().get("T9")).isEqualTo(quote(50));
  }

  @Test
  void failsClearlyWithoutMetadataOrWatchlist() {
    assertThatThrownBy(job::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No stock universe: run refresh-metadata first");
  }
}
