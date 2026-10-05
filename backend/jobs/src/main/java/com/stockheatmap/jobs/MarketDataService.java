package com.stockheatmap.jobs;

import com.stockheatmap.core.model.BaselinesFile;
import com.stockheatmap.core.model.CryptoFile;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.MarketData;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.StocksFile;
import com.stockheatmap.integrations.store.DataStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** GET /market-data: the four files the browser needs, read in parallel. */
public class MarketDataService {

  private final DataStore store;

  public MarketDataService(DataStore store) {
    this.store = store;
  }

  public MarketData load() {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var stocks = CompletableFuture.supplyAsync(() -> read(DataKey.STOCKS, StocksFile.class), executor);
      var crypto = CompletableFuture.supplyAsync(() -> read(DataKey.CRYPTO, CryptoFile.class), executor);
      var baselines = CompletableFuture.supplyAsync(() -> read(DataKey.BASELINES, BaselinesFile.class), executor);
      var metadata = CompletableFuture.supplyAsync(() -> read(DataKey.METADATA, MetadataFile.class), executor);
      return new MarketData(stocks.join(), crypto.join(), baselines.join(), metadata.join());
    }
  }

  private <T> T read(DataKey key, Class<T> type) {
    return store.get(key, type).orElse(null);
  }
}
