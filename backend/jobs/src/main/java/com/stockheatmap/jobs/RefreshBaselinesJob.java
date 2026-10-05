package com.stockheatmap.jobs;

import com.stockheatmap.core.Baselines;
import com.stockheatmap.core.EtTime;
import com.stockheatmap.core.Freshness;
import com.stockheatmap.core.Universe;
import com.stockheatmap.core.model.BaselinesFile;
import com.stockheatmap.core.model.CoinBaseline;
import com.stockheatmap.core.model.CryptoFile;
import com.stockheatmap.core.model.DailyBar;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.StockBaseline;
import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.http.Sleeper;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Weekdays 17:00 ET: daily closes for 1W/1M/YTD and Jan 1 crypto prices → baselines.json. */
public class RefreshBaselinesJob {

  private static final Logger log = LoggerFactory.getLogger(RefreshBaselinesJob.class);

  /** Free-plan SIP history must end at least 15 minutes ago. */
  static final Duration BARS_END_LAG = Duration.ofMinutes(20);
  /** CoinGecko Demo allows ~30 calls/min. */
  static final long COINGECKO_PACING_MS = 2500;

  private final DataStore store;
  private final WatchlistRepo watchlist;
  private final AlpacaClient alpaca;
  private final CoinGeckoClient coingecko;
  private final Clock clock;
  private final Sleeper sleeper;

  public RefreshBaselinesJob(DataStore store, WatchlistRepo watchlist, AlpacaClient alpaca,
      CoinGeckoClient coingecko, Clock clock, Sleeper sleeper) {
    this.store = store;
    this.watchlist = watchlist;
    this.alpaca = alpaca;
    this.coingecko = coingecko;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  public RefreshSummary run() {
    Instant now = clock.instant();
    Optional<BaselinesFile> previous = store.get(DataKey.BASELINES, BaselinesFile.class);

    List<String> universe = Universe.stocks(
        store.get(DataKey.METADATA, MetadataFile.class).orElse(null), watchlist.allItems());
    if (universe.isEmpty()) {
      throw new IllegalStateException("No stock universe: run refresh-metadata first");
    }
    Map<String, List<DailyBar>> bars =
        alpaca.getDailyBars(universe, Baselines.barsStartDate(now), now.minus(BARS_END_LAG));
    Map<String, StockBaseline> previousStocks = previous.map(BaselinesFile::stocks).orElse(Map.of());
    Map<String, StockBaseline> stocks = new LinkedHashMap<>();
    int fresh = 0;
    for (String symbol : universe) {
      Optional<StockBaseline> baseline = Baselines.derive(bars.getOrDefault(symbol, List.of()));
      if (baseline.isPresent()) {
        stocks.put(symbol, baseline.get());
        fresh++;
      } else if (previousStocks.containsKey(symbol)) {
        stocks.put(symbol, previousStocks.get(symbol));
      }
    }
    double freshRatio = (double) fresh / universe.size();
    Freshness.assertFresh(freshRatio, "baselines");

    Map<String, CoinBaseline> crypto = coinBaselines(now, previous.map(BaselinesFile::crypto).orElse(Map.of()));
    store.put(DataKey.BASELINES, new BaselinesFile(now.toString(), stocks, crypto));
    log.info("Wrote baselines.json: {} stocks ({} fresh), {} coins", stocks.size(), fresh, crypto.size());
    return new RefreshSummary(DataKey.BASELINES.key(), stocks.size(), freshRatio);
  }

  /** Jan 1 prices for the coins in crypto.json; each coin is looked up once per year. */
  private Map<String, CoinBaseline> coinBaselines(Instant now, Map<String, CoinBaseline> previous) {
    int year = Integer.parseInt(EtTime.etDate(now).substring(0, 4));
    Set<String> coinIds = store.get(DataKey.CRYPTO, CryptoFile.class).map(f -> f.coins().keySet()).orElse(Set.of());
    Map<String, CoinBaseline> crypto = new LinkedHashMap<>();
    boolean firstCall = true;
    for (String id : coinIds) {
      CoinBaseline known = previous.get(id);
      if (known != null && known.year() == year) {
        crypto.put(id, known);
        continue;
      }
      if (!firstCall) {
        sleeper.sleep(COINGECKO_PACING_MS);
      }
      firstCall = false;
      try {
        OptionalDouble price = coingecko.priceOn(id, LocalDate.of(year, 1, 1));
        if (price.isPresent()) {
          crypto.put(id, new CoinBaseline(price.getAsDouble(), year));
        }
      } catch (RuntimeException e) {
        log.warn("No Jan 1 price for {}: {}", id, e.getMessage());
      }
    }
    return crypto;
  }
}
