package com.stockheatmap.jobs;

import com.stockheatmap.core.Sp500Parser;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.Sp500Entry;
import com.stockheatmap.core.model.SymbolsFile;
import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.finnhub.FinnhubClient;
import com.stockheatmap.integrations.http.Sleeper;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.wikipedia.WikipediaClient;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sundays: S&P 500 list + sectors (Wikipedia) and shares outstanding (Finnhub) → metadata.json;
 * valid stock symbols (Alpaca) and coin ids (CoinGecko) → symbols.json.
 */
public class RefreshMetadataJob {

  private static final Logger log = LoggerFactory.getLogger(RefreshMetadataJob.class);

  /** Finnhub free tier allows 60 calls/min; stay at 55 (60000 / 55, rounded up). */
  static final long FINNHUB_PACING_MS = 1091;

  private final DataStore store;
  private final WikipediaClient wikipedia;
  private final FinnhubClient finnhub;
  private final AlpacaClient alpaca;
  private final CoinGeckoClient coingecko;
  private final Clock clock;
  private final Sleeper sleeper;

  public RefreshMetadataJob(DataStore store, WikipediaClient wikipedia, FinnhubClient finnhub,
      AlpacaClient alpaca, CoinGeckoClient coingecko, Clock clock, Sleeper sleeper) {
    this.store = store;
    this.wikipedia = wikipedia;
    this.finnhub = finnhub;
    this.alpaca = alpaca;
    this.coingecko = coingecko;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  public RefreshSummary run() {
    String now = clock.instant().toString();
    List<Sp500Entry> sp500 = Sp500Parser.parse(wikipedia.fetchSp500Html());
    Sp500Parser.validate(sp500); // throws → nothing written, previous metadata stays

    Map<String, Double> previousShares =
        store.get(DataKey.METADATA, MetadataFile.class).map(MetadataFile::sharesOutstanding).orElse(Map.of());
    Map<String, Double> shares = new LinkedHashMap<>();
    int fresh = 0;
    for (int i = 0; i < sp500.size(); i++) {
      if (i > 0) {
        sleeper.sleep(FINNHUB_PACING_MS);
      }
      String symbol = sp500.get(i).symbol();
      OptionalDouble value = fetchShares(symbol);
      if (value.isPresent()) {
        shares.put(symbol, value.getAsDouble());
        fresh++;
      } else if (previousShares.containsKey(symbol)) {
        shares.put(symbol, previousShares.get(symbol));
      }
    }
    store.put(DataKey.METADATA, new MetadataFile(now, sp500, shares));
    log.info("Wrote metadata.json: {} constituents, {} with fresh share counts", sp500.size(), fresh);

    try {
      store.put(DataKey.SYMBOLS, new SymbolsFile(now, alpaca.listActiveSymbols(), coingecko.listCoinIds()));
    } catch (RuntimeException e) {
      log.error("symbols.json not refreshed: {}", e.getMessage());
      throw e; // metadata.json is already written; failing the run still raises the alarm
    }
    return new RefreshSummary(DataKey.METADATA.key(), sp500.size(), (double) fresh / sp500.size());
  }

  private OptionalDouble fetchShares(String symbol) {
    try {
      return finnhub.sharesOutstanding(symbol);
    } catch (RuntimeException e) {
      log.warn("Shares outstanding for {} unavailable: {}", symbol, e.getMessage());
      return OptionalDouble.empty();
    }
  }
}
