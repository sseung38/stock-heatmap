package com.stockheatmap.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stockheatmap.core.InvalidScrapeException;
import com.stockheatmap.core.Sp500Parser;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.SymbolsFile;
import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.finnhub.FinnhubClient;
import com.stockheatmap.integrations.http.HttpStatusException;
import com.stockheatmap.integrations.store.MemoryDataStore;
import com.stockheatmap.integrations.wikipedia.WikipediaClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RefreshMetadataJobTest {

  private final MemoryDataStore store = new MemoryDataStore(JsonMapper.builder().build());
  private final WikipediaClient wikipedia = mock(WikipediaClient.class);
  private final FinnhubClient finnhub = mock(FinnhubClient.class);
  private final AlpacaClient alpaca = mock(AlpacaClient.class);
  private final CoinGeckoClient coingecko = mock(CoinGeckoClient.class);
  private final List<Long> sleeps = new ArrayList<>();
  private RefreshMetadataJob job;

  /** A constituents table shaped like Wikipedia's, with 500 rows covering all 11 sectors. */
  private static String sp500Html() {
    StringBuilder html = new StringBuilder(
        "<table id=\"constituents\"><tr><th>Symbol</th><th>Security</th><th>GICS Sector</th></tr>");
    for (int i = 0; i < 500; i++) {
      html.append("<tr><td>S").append(i).append("</td><td>Company ").append(i).append("</td><td>")
          .append(Sp500Parser.GICS_SECTORS.get(i % 11)).append("</td></tr>");
    }
    return html.append("</table>").toString();
  }

  @BeforeEach
  void setUp() {
    job = new RefreshMetadataJob(store, wikipedia, finnhub, alpaca, coingecko,
        Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC), sleeps::add);
    when(wikipedia.fetchSp500Html()).thenReturn(sp500Html());
    when(finnhub.sharesOutstanding(anyString())).thenReturn(OptionalDouble.of(1e9));
    when(alpaca.listActiveSymbols()).thenReturn(List.of("AAPL", "BRK.B"));
    when(coingecko.listCoinIds()).thenReturn(List.of("bitcoin"));
  }

  @Test
  void writesMetadataAndSymbols() {
    RefreshSummary summary = job.run();

    MetadataFile metadata = store.get(DataKey.METADATA, MetadataFile.class).orElseThrow();
    assertThat(metadata.asOf()).isEqualTo("2026-10-04T10:00:00Z");
    assertThat(metadata.sp500()).hasSize(500);
    assertThat(metadata.sharesOutstanding()).hasSize(500).containsEntry("S0", 1e9);
    assertThat(store.get(DataKey.SYMBOLS, SymbolsFile.class).orElseThrow())
        .isEqualTo(new SymbolsFile("2026-10-04T10:00:00Z", List.of("AAPL", "BRK.B"), List.of("bitcoin")));
    assertThat(summary).isEqualTo(new RefreshSummary("metadata.json", 500, 1.0));
  }

  @Test
  void pacesFinnhubCallsToFiftyFivePerMinute() {
    job.run();

    assertThat(sleeps).hasSize(499).containsOnly(1091L);
  }

  @Test
  void writesNothingWhenScrapeLooksWrong() {
    when(wikipedia.fetchSp500Html()).thenReturn("<html></html>");

    assertThatThrownBy(job::run).isInstanceOf(InvalidScrapeException.class);

    assertThat(store.raw(DataKey.METADATA)).isEmpty();
  }

  @Test
  void keepsPreviousSharesWhenFinnhubHasNone() {
    store.put(DataKey.METADATA, new MetadataFile("old", List.of(), Map.of("S1", 5e8)));
    when(finnhub.sharesOutstanding("S1")).thenReturn(OptionalDouble.empty());
    when(finnhub.sharesOutstanding("S2")).thenThrow(new HttpStatusException(500, "finnhub.io/api/v1/stock/profile2"));

    job.run();

    Map<String, Double> shares = store.get(DataKey.METADATA, MetadataFile.class).orElseThrow().sharesOutstanding();
    assertThat(shares).containsEntry("S1", 5e8).doesNotContainKey("S2");
  }

  @Test
  void stillWritesMetadataWhenSymbolListFails() {
    when(coingecko.listCoinIds()).thenThrow(new HttpStatusException(503, "api.coingecko.com/api/v3/coins/list"));

    assertThatThrownBy(job::run).isInstanceOf(HttpStatusException.class);

    assertThat(store.raw(DataKey.METADATA)).isPresent();
    assertThat(store.raw(DataKey.SYMBOLS)).isEmpty();
  }
}
