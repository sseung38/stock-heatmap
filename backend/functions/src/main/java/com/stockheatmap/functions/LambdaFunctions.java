package com.stockheatmap.functions;

import com.stockheatmap.jobs.RefreshBaselinesJob;
import com.stockheatmap.jobs.RefreshCryptoJob;
import com.stockheatmap.jobs.RefreshMetadataJob;
import com.stockheatmap.jobs.RefreshStocksJob;
import com.stockheatmap.jobs.RefreshSummary;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One bean per Lambda. The bean name is what each Lambda's spring_cloud_function_definition
 * selects. Scheduled jobs ignore their input (the scheduler sends "{}"); an exception propagates
 * so Lambda records the run as failed and the CloudWatch alarm can fire.
 */
@Configuration(proxyBeanMethods = false)
class LambdaFunctions {

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshStocks(RefreshStocksJob job) {
    return event -> job.run();
  }

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshCrypto(RefreshCryptoJob job) {
    return event -> job.run();
  }

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshBaselines(RefreshBaselinesJob job) {
    return event -> job.run();
  }

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshMetadata(RefreshMetadataJob job) {
    return event -> job.run();
  }
}
