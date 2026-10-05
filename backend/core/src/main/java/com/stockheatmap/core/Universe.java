package com.stockheatmap.core;

import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.Sp500Entry;
import com.stockheatmap.core.model.WatchlistItem;
import java.util.List;
import java.util.TreeSet;

/** Which tickers and coins a refresh should fetch. */
public final class Universe {

  private Universe() {}

  /** S&P 500 constituents plus watchlist stocks, sorted and distinct. */
  public static List<String> stocks(MetadataFile metadata, List<WatchlistItem> watchlist) {
    TreeSet<String> symbols = new TreeSet<>();
    if (metadata != null) {
      metadata.sp500().stream().map(Sp500Entry::symbol).forEach(symbols::add);
    }
    for (WatchlistItem item : watchlist) {
      if (item instanceof WatchlistItem.StockItem stock) {
        symbols.add(stock.symbol());
      }
    }
    return List.copyOf(symbols);
  }

  public static List<String> watchlistCoinIds(List<WatchlistItem> watchlist) {
    return watchlist.stream()
        .filter(WatchlistItem.CryptoItem.class::isInstance)
        .map(item -> ((WatchlistItem.CryptoItem) item).id())
        .distinct()
        .toList();
  }
}
