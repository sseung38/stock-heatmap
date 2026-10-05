package com.stockheatmap.local;

import com.stockheatmap.integrations.secrets.EnvSecretsProvider;
import com.stockheatmap.integrations.secrets.SecretsProvider;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.store.FsDataStore;
import com.stockheatmap.integrations.watchlist.FsWatchlistRepo;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import com.stockheatmap.jobs.JobsConfiguration;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

/**
 * The "local" profile's answers to the three things JobsConfiguration leaves open: files in .data/
 * instead of S3 and DynamoDB, and API keys from .env instead of SSM Parameter Store.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@Import(JobsConfiguration.class)
class LocalConfiguration {

  @Bean
  DataStore dataStore(@Value("${heatmap.data-dir}") String dataDir, JsonMapper mapper) {
    return new FsDataStore(Path.of(dataDir), mapper);
  }

  @Bean
  WatchlistRepo watchlistRepo(@Value("${heatmap.data-dir}") String dataDir, JsonMapper mapper) {
    return new FsWatchlistRepo(Path.of(dataDir, "watchlist.json"), mapper);
  }

  @Bean
  SecretsProvider secretsProvider(Environment environment) {
    return new EnvSecretsProvider(environment);
  }
}
