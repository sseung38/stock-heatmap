package com.stockheatmap.integrations.watchlist;

import com.stockheatmap.core.model.WatchlistItem;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Item lists are written with an explicit element type so every item keeps its "type" field. */
final class WatchlistJson {

  static final TypeReference<List<WatchlistItem>> ITEMS = new TypeReference<>() {};

  private WatchlistJson() {}

  static String write(ObjectMapper mapper, List<WatchlistItem> items) {
    return mapper.writerFor(ITEMS).writeValueAsString(items);
  }

  static List<WatchlistItem> read(ObjectMapper mapper, String json) {
    return mapper.readValue(json, ITEMS);
  }
}
