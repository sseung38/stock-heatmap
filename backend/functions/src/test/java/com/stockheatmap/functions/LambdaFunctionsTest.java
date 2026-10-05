package com.stockheatmap.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stockheatmap.jobs.RefreshBaselinesJob;
import com.stockheatmap.jobs.RefreshCryptoJob;
import com.stockheatmap.jobs.RefreshMetadataJob;
import com.stockheatmap.jobs.RefreshStocksJob;
import com.stockheatmap.jobs.RefreshSummary;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the real Lambda application context (without the "aws" profile, so no AWS clients) and
 * looks functions up by name — the same lookup FunctionInvoker does on Lambda using the
 * spring_cloud_function_definition environment variable.
 */
@SpringBootTest(classes = FunctionsApplication.class)
@ActiveProfiles("test")
class LambdaFunctionsTest {

  @Autowired private FunctionCatalog catalog;

  @MockitoBean private RefreshStocksJob stocks;
  @MockitoBean private RefreshCryptoJob crypto;
  @MockitoBean private RefreshBaselinesJob baselines;
  @MockitoBean private RefreshMetadataJob metadata;

  private RefreshSummary invoke(String name) {
    Function<Map<String, Object>, RefreshSummary> function = catalog.lookup(name);
    return function.apply(Map.of());
  }

  @Test
  void refreshFunctionsRunTheirJobs() {
    when(stocks.run()).thenReturn(new RefreshSummary("stocks.json", 503, 1.0));
    when(crypto.run()).thenReturn(new RefreshSummary("crypto.json", 50, 1.0));
    when(baselines.run()).thenReturn(new RefreshSummary("baselines.json", 503, 0.99));
    when(metadata.run()).thenReturn(new RefreshSummary("metadata.json", 503, 1.0));

    assertThat(invoke("refreshStocks")).isEqualTo(new RefreshSummary("stocks.json", 503, 1.0));
    assertThat(invoke("refreshCrypto").file()).isEqualTo("crypto.json");
    assertThat(invoke("refreshBaselines").file()).isEqualTo("baselines.json");
    assertThat(invoke("refreshMetadata").file()).isEqualTo("metadata.json");
  }
}
