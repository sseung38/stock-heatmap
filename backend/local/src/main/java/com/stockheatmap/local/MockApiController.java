package com.stockheatmap.local;

import com.stockheatmap.core.model.MarketData;
import com.stockheatmap.jobs.ApiResult;
import com.stockheatmap.jobs.MarketDataService;
import com.stockheatmap.jobs.WatchlistService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * The same API the Lambdas serve, for the React dev server (Vite proxies /api and /config.json here).
 * No login locally: every request acts as the user "local".
 */
@RestController
class MockApiController {

  static final String LOCAL_USER = "local";
  static final String LOCAL_CONFIG = "{\"apiUrl\":\"/api\",\"auth\":null}";

  private final MarketDataService marketData;
  private final WatchlistService watchlist;
  private final String configFile;

  MockApiController(MarketDataService marketData, WatchlistService watchlist,
      @Value("${heatmap.config-file:}") String configFile) {
    this.marketData = marketData;
    this.watchlist = watchlist;
    this.configFile = configFile;
  }

  @GetMapping(value = "/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
  String config() throws IOException {
    return configFile.isBlank() ? LOCAL_CONFIG : Files.readString(Path.of(configFile));
  }

  @GetMapping("/api/market-data")
  MarketData marketData() {
    return marketData.load();
  }

  @RequestMapping(value = "/api/watchlist", method = {RequestMethod.GET, RequestMethod.PUT})
  ResponseEntity<Object> watchlist(HttpMethod method, @RequestBody(required = false) String body) {
    ApiResult result = watchlist.handle(method.name(), LOCAL_USER, body);
    return ResponseEntity.status(result.status()).body(result.body());
  }
}
