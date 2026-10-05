package com.stockheatmap.integrations.watchlist;

import com.stockheatmap.core.model.Watchlist;
import com.stockheatmap.core.model.WatchlistItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryWatchlistRepo implements WatchlistRepo {

  private final Map<String, Watchlist> lists = new ConcurrentHashMap<>();

  @Override
  public Watchlist get(String userId) {
    return lists.getOrDefault(userId, new Watchlist(List.of(), null));
  }

  @Override
  public Watchlist put(String userId, List<WatchlistItem> items) {
    Watchlist saved = new Watchlist(List.copyOf(items), Instant.now().toString());
    lists.put(userId, saved);
    return saved;
  }

  @Override
  public List<WatchlistItem> allItems() {
    return lists.values().stream().flatMap(w -> w.items().stream()).toList();
  }
}
