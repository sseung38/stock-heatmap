package com.stockheatmap.integrations.coingecko;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stockheatmap.core.model.CoinGeckoMarket;
import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoinGeckoClientTest {

  private static final String MARKETS = "https://api.coingecko.com/api/v3/coins/markets";

  private MockRestServiceServer server;
  private CoinGeckoClient coingecko;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    coingecko = new CoinGeckoClient(new RetryingHttp(builder.build(), ms -> {}), name -> "demo-key");
  }

  @Test
  void fetchesTopMarketsWithChangesAndSparkline() {
    server.expect(once(), requestTo(allOf(startsWith(MARKETS), containsString("per_page=50"), containsString("page=1"),
            containsString("sparkline=true"), containsString("price_change_percentage=24h,7d,30d"))))
        .andExpect(header("x-cg-demo-api-key", "demo-key"))
        .andRespond(withSuccess(Fixtures.read("coingecko-markets.json"), MediaType.APPLICATION_JSON));

    List<CoinGeckoMarket> markets = coingecko.topMarkets(50);

    assertThat(markets).extracting(CoinGeckoMarket::id).containsExactly("bitcoin", "ethereum", "binancecoin");
    CoinGeckoMarket btc = markets.getFirst();
    assertThat(btc.symbol()).isEqualTo("btc");
    assertThat(btc.price_change_percentage_24h_in_currency()).isEqualTo(0.82804);
    assertThat(btc.sparkline_in_7d().price()).hasSize(168);
    server.verify();
  }

  @Test
  void fetchesMarketsByIds() {
    server.expect(once(), requestTo(allOf(startsWith(MARKETS), containsString("ids=solana,pepe"))))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(coingecko.marketsByIds(List.of("solana", "pepe"))).isEmpty();
    server.verify();
  }

  @Test
  void readsPriceOnADate() {
    server.expect(once(), requestTo(
            "https://api.coingecko.com/api/v3/coins/bitcoin/history?date=01-01-2026&localization=false"))
        .andRespond(withSuccess(Fixtures.read("coingecko-history.json"), MediaType.APPLICATION_JSON));

    assertThat(coingecko.priceOn("bitcoin", LocalDate.parse("2026-01-01")).getAsDouble()).isEqualTo(87575.04615968224);
  }

  @Test
  void returnsEmptyWhenNoPriceForDate() {
    server.expect(once(), requestTo(startsWith("https://api.coingecko.com/api/v3/coins/newcoin/history")))
        .andRespond(withSuccess("{\"id\":\"newcoin\"}", MediaType.APPLICATION_JSON));

    assertThat(coingecko.priceOn("newcoin", LocalDate.parse("2026-01-01"))).isEmpty();
  }

  @Test
  void listsCoinIds() {
    server.expect(once(), requestTo("https://api.coingecko.com/api/v3/coins/list"))
        .andRespond(withSuccess(Fixtures.read("coingecko-list.json"), MediaType.APPLICATION_JSON));

    assertThat(coingecko.listCoinIds()).contains("bitcoin", "solana");
  }
}
