package com.stockheatmap.integrations.finnhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FinnhubClientTest {

  private MockRestServiceServer server;
  private FinnhubClient finnhub;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    finnhub = new FinnhubClient(new RetryingHttp(builder.build(), ms -> {}), name -> "tok");
  }

  @Test
  void convertsSharesOutstandingFromMillions() {
    server.expect(once(), requestTo("https://finnhub.io/api/v1/stock/profile2?symbol=AAPL&token=tok"))
        .andRespond(withSuccess(Fixtures.read("finnhub-profile2.json"), MediaType.APPLICATION_JSON));

    assertThat(finnhub.sharesOutstanding("AAPL").getAsDouble()).isEqualTo(14687.36 * 1e6);
  }

  @Test
  void returnsEmptyForUnknownSymbol() {
    server.expect(once(), requestTo("https://finnhub.io/api/v1/stock/profile2?symbol=ZZZZ&token=tok"))
        .andRespond(withSuccess(Fixtures.read("finnhub-profile2-empty.json"), MediaType.APPLICATION_JSON));

    assertThat(finnhub.sharesOutstanding("ZZZZ")).isEmpty();
  }
}
