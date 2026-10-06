package com.stockheatmap.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.stockheatmap.core.model.MarketData;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.Sp500Entry;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext.Authorizer;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext.Http;
import com.stockheatmap.jobs.ApiResult;
import com.stockheatmap.jobs.MarketDataService;
import com.stockheatmap.jobs.WatchlistService;
import com.stockheatmap.jobs.RefreshBaselinesJob;
import com.stockheatmap.jobs.RefreshCryptoJob;
import com.stockheatmap.jobs.RefreshMetadataJob;
import com.stockheatmap.jobs.RefreshStocksJob;
import com.stockheatmap.jobs.RefreshSummary;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots the real Lambda application context (without the "aws" profile, so no AWS clients) and
 * looks functions up by name — the same lookup FunctionInvoker does on Lambda using the
 * spring_cloud_function_definition environment variable.
 */
@SpringBootTest(classes = FunctionsApplication.class)
@ActiveProfiles("test")
@Import(LambdaFunctionsTest.Mapper.class)
class LambdaFunctionsTest {

  /** On Lambda the mapper comes from JobsConfiguration (aws profile, see AwsProfileContextTest). */
  @TestConfiguration
  static class Mapper {
    @Bean
    JsonMapper testJsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @Autowired private FunctionCatalog catalog;

  @MockitoBean private RefreshStocksJob stocks;
  @MockitoBean private RefreshCryptoJob crypto;
  @MockitoBean private RefreshBaselinesJob baselines;
  @MockitoBean private RefreshMetadataJob metadata;
  @MockitoBean private MarketDataService marketData;
  @MockitoBean private WatchlistService watchlist;
  @Autowired private JsonMapper mapper;

  private static final MarketData DATA = new MarketData(null, null, null,
      new MetadataFile("2026-10-04T06:00:00Z", List.of(new Sp500Entry("AAPL", "Apple Inc.", "Information Technology")), Map.of()));

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

  private APIGatewayV2HTTPResponse getMarketData(Map<String, String> headers) {
    Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> function = catalog.lookup("getMarketData");
    return function.apply(APIGatewayV2HTTPEvent.builder().withHeaders(headers).build());
  }

  @Test
  void getMarketDataGzipsWhenTheBrowserAcceptsIt() throws IOException {
    when(marketData.load()).thenReturn(DATA);

    APIGatewayV2HTTPResponse response = getMarketData(Map.of("accept-encoding", "gzip, deflate, br"));

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getIsBase64Encoded()).isTrue();
    assertThat(response.getHeaders())
        .containsEntry("content-type", "application/json")
        .containsEntry("content-encoding", "gzip")
        .containsEntry("cache-control", "private, max-age=30");
    byte[] gzipped = Base64.getDecoder().decode(response.getBody());
    try (var in = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(mapper.writeValueAsString(DATA));
    }
  }

  @Test
  void getMarketDataSendsPlainJsonOtherwise() {
    when(marketData.load()).thenReturn(DATA);

    APIGatewayV2HTTPResponse response = getMarketData(null);

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getIsBase64Encoded()).isFalse();
    assertThat(response.getHeaders()).doesNotContainKey("content-encoding");
    assertThat(response.getBody()).isEqualTo(mapper.writeValueAsString(DATA));
  }

  private static APIGatewayV2HTTPEvent watchlistEvent(String method, String sub, String body, boolean base64) {
    return APIGatewayV2HTTPEvent.builder()
        .withRequestContext(RequestContext.builder()
            .withHttp(Http.builder().withMethod(method).build())
            .withAuthorizer(Authorizer.builder().withJwt(Authorizer.JWT.builder().withClaims(Map.of("sub", sub)).build()).build())
            .build())
        .withBody(body)
        .withIsBase64Encoded(base64)
        .build();
  }

  private APIGatewayV2HTTPResponse callWatchlist(APIGatewayV2HTTPEvent event) {
    Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> function = catalog.lookup("watchlist");
    return function.apply(event);
  }

  @Test
  void watchlistActsAsTheSignedInUser() {
    String body = "{\"items\":[{\"type\":\"stock\",\"symbol\":\"PLTR\"}]}";
    when(watchlist.handle("PUT", "u1", body)).thenReturn(new ApiResult(400, Map.of("error", "Unknown or duplicate items")));

    APIGatewayV2HTTPResponse response = callWatchlist(watchlistEvent("PUT", "u1", body, false));

    verify(watchlist).handle("PUT", "u1", body);
    assertThat(response.getStatusCode()).isEqualTo(400);
    assertThat(response.getBody()).contains("Unknown or duplicate items");
  }

  @Test
  void watchlistDecodesBase64Bodies() {
    String body = "{\"items\":[]}";
    String encoded = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    when(watchlist.handle("PUT", "u1", body)).thenReturn(new ApiResult(200, Map.of("items", List.of())));

    APIGatewayV2HTTPResponse response = callWatchlist(watchlistEvent("PUT", "u1", encoded, true));

    verify(watchlist).handle("PUT", "u1", body);
    assertThat(response.getStatusCode()).isEqualTo(200);
  }
}
