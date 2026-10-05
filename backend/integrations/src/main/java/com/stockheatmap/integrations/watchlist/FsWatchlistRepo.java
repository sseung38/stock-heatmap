package com.stockheatmap.integrations.watchlist;

import com.stockheatmap.core.model.Watchlist;
import com.stockheatmap.core.model.WatchlistItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** One JSON file holding every user's watchlist ({@code {"<userId>": Watchlist}}), for local runs. */
public class FsWatchlistRepo implements WatchlistRepo {

  private static final TypeReference<Map<String, Watchlist>> ALL = new TypeReference<>() {};

  private final Path file;
  private final ObjectMapper mapper;

  public FsWatchlistRepo(Path file, ObjectMapper mapper) {
    this.file = file;
    this.mapper = mapper;
  }

  @Override
  public synchronized Watchlist get(String userId) {
    return readAll().getOrDefault(userId, new Watchlist(List.of(), null));
  }

  @Override
  public synchronized Watchlist put(String userId, List<WatchlistItem> items) {
    Map<String, Watchlist> all = readAll();
    Watchlist saved = new Watchlist(List.copyOf(items), Instant.now().toString());
    all.put(userId, saved);
    try {
      Files.createDirectories(file.getParent());
      mapper.writerFor(ALL).writeValue(file.toFile(), all);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return saved;
  }

  @Override
  public synchronized List<WatchlistItem> allItems() {
    return readAll().values().stream().flatMap(w -> w.items().stream()).toList();
  }

  private Map<String, Watchlist> readAll() {
    return Files.exists(file) ? new LinkedHashMap<>(mapper.readValue(file.toFile(), ALL)) : new LinkedHashMap<>();
  }
}
