package com.stockheatmap.integrations.watchlist;

import com.stockheatmap.core.model.Watchlist;
import com.stockheatmap.core.model.WatchlistItem;
import java.util.List;

public interface WatchlistRepo {

  /** The user's watchlist, or an empty one (updatedAt null) if they never saved one. */
  Watchlist get(String userId);

  /** Replaces the whole list and stamps updatedAt. */
  Watchlist put(String userId, List<WatchlistItem> items);

  /** Items of every user; refresh jobs add these to what they fetch. */
  List<WatchlistItem> allItems();
}
