package com.stockheatmap.jobs;

import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.coinbase.CoinbaseClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.finnhub.FinnhubClient;
import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.http.Sleeper;
import com.stockheatmap.integrations.secrets.SecretsProvider;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import com.stockheatmap.integrations.wikipedia.WikipediaClient;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The object graph shared by the Lambda app and the local app. Each @Bean method says how to
 * build one object; Spring calls them in dependency order and passes results into the parameters
 * of other @Bean methods (dependency injection).
 *
 * <p>Not defined here, on purpose: {@link DataStore}, {@link WatchlistRepo} and {@link
 * SecretsProvider}. The active profile supplies those — S3/DynamoDB/SSM on AWS ("aws"), files and
 * .env on your machine ("local") — so the same jobs run unchanged in both places.
 */
@Configuration(proxyBeanMethods = false)
public class JobsConfiguration {

  @Bean
  JsonMapper jsonMapper() {
    return JsonMapper.builder().build();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  Sleeper sleeper() {
    return Sleeper.REAL;
  }

  /** Uses Spring Boot's pre-configured RestClient.Builder when there is one. */
  @Bean
  RetryingHttp retryingHttp(ObjectProvider<RestClient.Builder> builder, Sleeper sleeper) {
    return new RetryingHttp(builder.getIfAvailable(RestClient::builder).build(), sleeper);
  }

  // --- external API clients ---

  @Bean
  AlpacaClient alpacaClient(RetryingHttp http, SecretsProvider secrets) {
    return new AlpacaClient(http, secrets);
  }

  @Bean
  CoinGeckoClient coinGeckoClient(RetryingHttp http, SecretsProvider secrets) {
    return new CoinGeckoClient(http, secrets);
  }

  @Bean
  CoinbaseClient coinbaseClient(RetryingHttp http) {
    return new CoinbaseClient(http);
  }

  @Bean
  FinnhubClient finnhubClient(RetryingHttp http, SecretsProvider secrets) {
    return new FinnhubClient(http, secrets);
  }

  @Bean
  WikipediaClient wikipediaClient(RetryingHttp http) {
    return new WikipediaClient(http);
  }

  // --- refresh jobs ---

  @Bean
  RefreshStocksJob refreshStocksJob(DataStore store, WatchlistRepo watchlist, AlpacaClient alpaca, Clock clock) {
    return new RefreshStocksJob(store, watchlist, alpaca, clock);
  }

  @Bean
  RefreshCryptoJob refreshCryptoJob(DataStore store, WatchlistRepo watchlist, CoinGeckoClient coingecko,
      CoinbaseClient coinbase, Clock clock) {
    return new RefreshCryptoJob(store, watchlist, coingecko, coinbase, clock);
  }

  @Bean
  RefreshBaselinesJob refreshBaselinesJob(DataStore store, WatchlistRepo watchlist, AlpacaClient alpaca,
      CoinGeckoClient coingecko, Clock clock, Sleeper sleeper) {
    return new RefreshBaselinesJob(store, watchlist, alpaca, coingecko, clock, sleeper);
  }

  @Bean
  RefreshMetadataJob refreshMetadataJob(DataStore store, WikipediaClient wikipedia, FinnhubClient finnhub,
      AlpacaClient alpaca, CoinGeckoClient coingecko, Clock clock, Sleeper sleeper) {
    return new RefreshMetadataJob(store, wikipedia, finnhub, alpaca, coingecko, clock, sleeper);
  }

  // --- API services ---

  @Bean
  MarketDataService marketDataService(DataStore store) {
    return new MarketDataService(store);
  }

  @Bean
  WatchlistService watchlistService(WatchlistRepo repo, DataStore store, JsonMapper mapper) {
    return new WatchlistService(repo, store, mapper);
  }
}
