package com.stockheatmap.jobs;

import com.stockheatmap.core.Freshness;
import com.stockheatmap.core.Merge;
import com.stockheatmap.core.MergeResult;
import com.stockheatmap.core.Universe;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.Market;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.StockQuote;
import com.stockheatmap.core.model.StocksFile;
import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every 5 minutes in market hours: delayed quotes for the S&P 500 + watchlist stocks → stocks.json. */
public class RefreshStocksJob {

  private static final Logger log = LoggerFactory.getLogger(RefreshStocksJob.class);

  private final DataStore store;
  private final WatchlistRepo watchlist;
  private final AlpacaClient alpaca;
  private final Clock clock;

  public RefreshStocksJob(DataStore store, WatchlistRepo watchlist, AlpacaClient alpaca, Clock clock) {
    this.store = store;
    this.watchlist = watchlist;
    this.alpaca = alpaca;
    this.clock = clock;
  }

  public RefreshSummary run() {
    MetadataFile metadata = store.get(DataKey.METADATA, MetadataFile.class).orElse(null);
    List<String> universe = Universe.stocks(metadata, watchlist.allItems());
    if (universe.isEmpty()) {
      throw new IllegalStateException("No stock universe: run refresh-metadata first");
    }

    boolean isOpen = alpaca.getClock().isOpen();
    Map<String, StockQuote> fresh = alpaca.getSnapshots(universe);
    Map<String, StockQuote> previous = store.get(DataKey.STOCKS, StocksFile.class).map(StocksFile::quotes).orElse(null);
    MergeResult<StockQuote> result = Merge.snapshot(previous, fresh, universe);
    Freshness.assertFresh(result.freshRatio(), "stocks");

    store.put(DataKey.STOCKS, new StocksFile(clock.instant().toString(), new Market(isOpen), result.merged()));
    log.info("Wrote stocks.json: {} quotes, {} fresh, market open={}", result.merged().size(), fresh.size(), isOpen);
    return new RefreshSummary(DataKey.STOCKS.key(), result.merged().size(), result.freshRatio());
  }
}
