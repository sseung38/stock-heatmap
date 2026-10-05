package com.stockheatmap.functions;

import com.stockheatmap.integrations.secrets.SecretsProvider;
import com.stockheatmap.integrations.secrets.SsmSecretsProvider;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.store.S3DataStore;
import com.stockheatmap.integrations.watchlist.DynamoWatchlistRepo;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import com.stockheatmap.jobs.JobsConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ssm.SsmClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The "aws" profile's answers to what JobsConfiguration leaves open. Region and credentials come
 * from the Lambda environment (AWS_REGION and the function's IAM role); bucket and table names
 * come from environment variables set by CDK.
 */
@Configuration(proxyBeanMethods = false)
@Profile("aws")
@Import(JobsConfiguration.class)
class AwsConfiguration {

  @Bean
  S3Client s3Client() {
    return S3Client.builder().httpClient(UrlConnectionHttpClient.create()).build();
  }

  @Bean
  DynamoDbClient dynamoDbClient() {
    return DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
  }

  @Bean
  DataStore dataStore(S3Client s3, @Value("${DATA_BUCKET}") String bucket, JsonMapper mapper) {
    return new S3DataStore(s3, bucket, mapper);
  }

  @Bean
  WatchlistRepo watchlistRepo(DynamoDbClient dynamo, @Value("${WATCHLIST_TABLE}") String table, JsonMapper mapper) {
    return new DynamoWatchlistRepo(dynamo, table, mapper);
  }

  /** Created lazily inside the provider, so nothing is fetched before the SnapStart snapshot. */
  @Bean
  SecretsProvider secretsProvider() {
    return new SsmSecretsProvider(() -> SsmClient.builder().httpClient(UrlConnectionHttpClient.create()).build());
  }
}
