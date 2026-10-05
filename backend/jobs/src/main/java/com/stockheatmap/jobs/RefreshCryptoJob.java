package com.stockheatmap.jobs;

import com.stockheatmap.core.CryptoMapping;
import com.stockheatmap.core.Freshness;
import com.stockheatmap.core.Merge;
import com.stockheatmap.core.MergeResult;
import com.stockheatmap.core.Universe;
import com.stockheatmap.core.model.CoinGeckoMarket;
import com.stockheatmap.core.model.CoinQuote;
import com.stockheatmap.core.model.CryptoFile;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.integrations.coinbase.CoinbaseClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every 15 minutes: top 50 coins + watchlist coins from CoinGecko → crypto.json. */
public class RefreshCryptoJob {

  private static final Logger log = LoggerFactory.getLogger(RefreshCryptoJob.class);

  static final int TOP_N = 50;

  private final DataStore store;
  private final WatchlistRepo watchlist;
  private final CoinGeckoClient coingecko;
  private final CoinbaseClient coinbase;
  private final Clock clock;

  public RefreshCryptoJob(DataStore store, WatchlistRepo watchlist, CoinGeckoClient coingecko,
      CoinbaseClient coinbase, Clock clock) {
    this.store = store;
    this.watchlist = watchlist;
    this.coingecko = coingecko;
    this.coinbase = coinbase;
    this.clock = clock;
  }

  public RefreshSummary run() {
    String now = clock.instant().toString();
    List<CoinGeckoMarket> markets = new ArrayList<>(coingecko.topMarkets(TOP_N)); // failure → nothing written

    Set<String> universe = new LinkedHashSet<>();
    markets.forEach(m -> universe.add(m.id()));
    List<String> extraIds = Universe.watchlistCoinIds(watchlist.allItems()).stream()
        .filter(id -> !universe.contains(id))
        .toList();
    if (!extraIds.isEmpty()) {
      universe.addAll(extraIds);
      try {
        markets.addAll(coingecko.marketsByIds(extraIds));
      } catch (RuntimeException e) {
        log.warn("Watchlist coins {} not refreshed: {}", extraIds, e.getMessage());
      }
    }

    Set<String> usdProducts = coinbase.listUsdProducts();
    Map<String, CoinQuote> fresh = new LinkedHashMap<>();
    markets.forEach(m -> fresh.put(m.id(), CryptoMapping.toCoinQuote(m, usdProducts, now)));
    Map<String, CoinQuote> previous = store.get(DataKey.CRYPTO, CryptoFile.class).map(CryptoFile::coins).orElse(null);
    MergeResult<CoinQuote> result = Merge.snapshot(previous, fresh, universe);
    Freshness.assertFresh(result.freshRatio(), "crypto");

    store.put(DataKey.CRYPTO, new CryptoFile(now, result.merged()));
    log.info("Wrote crypto.json: {} coins ({} from watchlist)", result.merged().size(), extraIds.size());
    return new RefreshSummary(DataKey.CRYPTO.key(), result.merged().size(), result.freshRatio());
  }
}
