package com.stockheatmap.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.secrets.SecretsProvider;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.store.MemoryDataStore;
import com.stockheatmap.integrations.watchlist.MemoryWatchlistRepo;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts a real Spring context with JobsConfiguration plus the three beans a profile must supply
 * (DataStore, WatchlistRepo, SecretsProvider), and checks Spring can build the whole object graph.
 */
class JobsConfigurationTest {

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(JobsConfiguration.class)
      .withBean(DataStore.class, () -> new MemoryDataStore(JsonMapper.builder().build()))
      .withBean(WatchlistRepo.class, MemoryWatchlistRepo::new)
      .withBean(SecretsProvider.class, () -> name -> "test-key");

  @Test
  void wiresEveryJobAndService() {
    context.run(ctx -> assertThat(ctx)
        .hasNotFailed()
        .hasSingleBean(AlpacaClient.class)
        .hasSingleBean(RefreshStocksJob.class)
        .hasSingleBean(RefreshCryptoJob.class)
        .hasSingleBean(RefreshBaselinesJob.class)
        .hasSingleBean(RefreshMetadataJob.class)
        .hasSingleBean(MarketDataService.class)
        .hasSingleBean(WatchlistService.class));
  }

  @Test
  void failsToStartWithoutAProfileSuppliedDataStore() {
    new ApplicationContextRunner()
        .withUserConfiguration(JobsConfiguration.class)
        .withBean(WatchlistRepo.class, MemoryWatchlistRepo::new)
        .withBean(SecretsProvider.class, () -> name -> "test-key")
        .run(ctx -> assertThat(ctx).hasFailed());
  }
}
