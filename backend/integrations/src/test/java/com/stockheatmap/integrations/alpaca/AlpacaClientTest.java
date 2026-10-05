package com.stockheatmap.integrations.alpaca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stockheatmap.core.model.DailyBar;
import com.stockheatmap.core.model.StockQuote;
import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.secrets.SecretName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Uses responses recorded from the real Alpaca API (src/test/resources/fixtures). */
class AlpacaClientTest {

  private static final String SNAPSHOTS = "https://data.alpaca.markets/v2/stocks/snapshots";
  private static final String BARS = "https://data.alpaca.markets/v2/stocks/bars";

  private MockRestServiceServer server;
  private AlpacaClient alpaca;

  static String fixture(String name) {
    try (var in = AlpacaClientTest.class.getResourceAsStream("/fixtures/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    Map<SecretName, String> secrets = Map.of(SecretName.ALPACA_KEY_ID, "key", SecretName.ALPACA_SECRET, "secret");
    alpaca = new AlpacaClient(new RetryingHttp(builder.build(), ms -> {}), secrets::get);
  }

  @Test
  void readsMarketClockWithAuthHeaders() {
    server.expect(once(), requestTo("https://paper-api.alpaca.markets/v2/clock"))
        .andExpect(header("APCA-API-KEY-ID", "key"))
        .andExpect(header("APCA-API-SECRET-KEY", "secret"))
        .andRespond(withSuccess(fixture("alpaca-clock.json"), MediaType.APPLICATION_JSON));

    MarketClock clock = alpaca.getClock();

    assertThat(clock.isOpen()).isFalse(); // recorded on a weekend
    server.verify();
  }

  @Test
  void mapsSnapshotsKeepingShareClassTickers() {
    server.expect(once(), requestTo(allOf(startsWith(SNAPSHOTS), containsString("feed=delayed_sip"))))
        .andRespond(withSuccess(fixture("alpaca-snapshots.json"), MediaType.APPLICATION_JSON));

    Map<String, StockQuote> quotes = alpaca.getSnapshots(List.of("AAPL", "BRK.B", "MSFT"));

    assertThat(quotes).containsOnlyKeys("AAPL", "BRK.B", "MSFT");
    // Regular-session price (dailyBar close), not the after-hours latest trade in the recording.
    assertThat(quotes.get("MSFT")).isEqualTo(
        new StockQuote(517.53, 512.8, 18433500, "2026-10-02", "2026-10-02T23:59:48.948856303Z"));
    assertThat(quotes.get("BRK.B").tradingDay()).matches("\\d{4}-\\d{2}-\\d{2}");
  }

  @Test
  void splitsSnapshotRequestsIntoBatchesOf100() {
    server.expect(times(3), requestTo(startsWith(SNAPSHOTS))).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    alpaca.getSnapshots(IntStream.range(0, 250).mapToObj(i -> "T" + i).toList());

    server.verify();
  }

  @Test
  void omitsAFailedBatchInsteadOfThrowing() {
    server.expect(times(4), requestTo(startsWith(SNAPSHOTS))).andRespond(withServerError());

    assertThat(alpaca.getSnapshots(List.of("AAPL"))).isEmpty();
  }

  @Test
  void followsBarPagination() {
    server.expect(once(), requestTo(allOf(startsWith(BARS), not(containsString("page_token=")),
            containsString("adjustment=split"), containsString("feed=sip"), containsString("start=2026-08-01"))))
        .andRespond(withSuccess(fixture("alpaca-bars-page1.json"), MediaType.APPLICATION_JSON));
    server.expect(once(), requestTo(allOf(startsWith(BARS), containsString("page_token="))))
        .andRespond(withSuccess(fixture("alpaca-bars-page2.json"), MediaType.APPLICATION_JSON));

    Map<String, List<DailyBar>> bars =
        alpaca.getDailyBars(List.of("AAPL", "MSFT"), LocalDate.parse("2026-08-01"), Instant.parse("2026-10-03T00:00:00Z"));

    assertThat(bars.get("AAPL")).hasSize(44);
    assertThat(bars.get("MSFT")).hasSize(44); // 6 on page 1 + 38 on page 2
    List<String> msftDates = bars.get("MSFT").stream().map(DailyBar::date).toList();
    assertThat(msftDates).isSorted().doesNotHaveDuplicates();
    server.verify();
  }

  @Test
  void listsActiveSymbols() {
    server.expect(once(), requestTo(startsWith("https://paper-api.alpaca.markets/v2/assets?status=active")))
        .andRespond(withSuccess(fixture("alpaca-assets.json"), MediaType.APPLICATION_JSON));

    assertThat(alpaca.listActiveSymbols()).contains("AAPL", "BRK.B");
  }
}
