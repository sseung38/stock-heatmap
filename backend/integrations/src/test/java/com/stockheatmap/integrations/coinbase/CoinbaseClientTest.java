package com.stockheatmap.integrations.coinbase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoinbaseClientTest {

  @Test
  void keepsOnlyOnlineUsdProducts() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(once(), requestTo("https://api.exchange.coinbase.com/products"))
        .andRespond(withSuccess(Fixtures.read("coinbase-products.json"), MediaType.APPLICATION_JSON));

    var products = new CoinbaseClient(new RetryingHttp(builder.build(), ms -> {})).listUsdProducts();

    assertThat(products).containsExactlyInAnyOrder("BTC-USD", "ETH-USD", "SOL-USD");
  }
}
