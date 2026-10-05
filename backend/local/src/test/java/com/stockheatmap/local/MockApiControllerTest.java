package com.stockheatmap.local;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockheatmap.core.model.Market;
import com.stockheatmap.core.model.MarketData;
import com.stockheatmap.core.model.StockQuote;
import com.stockheatmap.core.model.StocksFile;
import com.stockheatmap.core.model.WatchlistItem.StockItem;
import com.stockheatmap.jobs.ApiResult;
import com.stockheatmap.jobs.ErrorBody;
import com.stockheatmap.jobs.MarketDataService;
import com.stockheatmap.jobs.WatchlistService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A "slice" test: starts only Spring MVC and this controller; the services are Mockito mocks. */
@WebMvcTest(MockApiController.class)
class MockApiControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private MarketDataService marketData;
  @MockitoBean private WatchlistService watchlist;

  @Test
  void servesMarketData() throws Exception {
    when(marketData.load()).thenReturn(new MarketData(
        new StocksFile("t", new Market(true), Map.of("AAPL", new StockQuote(227.5, 225.1, 1, "2026-10-02", "t"))),
        null, null, null));

    mvc.perform(get("/api/market-data"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stocks.quotes.AAPL.price").value(227.5))
        .andExpect(jsonPath("$.stocks.market.isOpen").value(true))
        .andExpect(jsonPath("$.crypto").isEmpty());
  }

  @Test
  void servesLocalConfigWithAuthDisabled() throws Exception {
    mvc.perform(get("/config.json"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"apiUrl\":\"/api\",\"auth\":null}"));
  }

  @Test
  void passesWatchlistPutToTheServiceAsLocalUser() throws Exception {
    String body = "{\"items\":[{\"type\":\"stock\",\"symbol\":\"ZZZZ\"}]}";
    when(watchlist.handle("PUT", "local", body))
        .thenReturn(new ApiResult(400, new ErrorBody("Unknown or duplicate items", List.of(new StockItem("ZZZZ")))));

    mvc.perform(put("/api/watchlist").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.invalid[0].type").value("stock"))
        .andExpect(jsonPath("$.invalid[0].symbol").value("ZZZZ"));
  }
}
