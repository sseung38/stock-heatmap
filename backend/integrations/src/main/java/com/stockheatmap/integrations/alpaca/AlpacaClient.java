package com.stockheatmap.integrations.alpaca;

import com.stockheatmap.core.EtTime;
import com.stockheatmap.core.model.DailyBar;
import com.stockheatmap.core.model.StockQuote;
import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.secrets.SecretName;
import com.stockheatmap.integrations.secrets.SecretsProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/** Alpaca market data (free plan) and the two trading-API endpoints the app needs. */
public class AlpacaClient {

  private static final Logger log = LoggerFactory.getLogger(AlpacaClient.class);

  static final String TRADING = "https://paper-api.alpaca.markets/v2";
  static final String DATA = "https://data.alpaca.markets/v2/stocks";
  static final int BATCH = 100;

  private final RetryingHttp http;
  private final SecretsProvider secrets;

  public AlpacaClient(RetryingHttp http, SecretsProvider secrets) {
    this.http = http;
    this.secrets = secrets;
  }

  public MarketClock getClock() {
    JsonNode clock = http.getJson(TRADING + "/clock", headers(), JsonNode.class);
    return new MarketClock(clock.get("is_open").asBoolean());
  }

  /**
   * Latest 15-minute-delayed quotes. The price is the regular-session daily bar close, so trades
   * after 16:00 ET do not leak into the heatmap. A batch that keeps failing is logged and skipped.
   */
  public Map<String, StockQuote> getSnapshots(List<String> symbols) {
    Map<String, StockQuote> quotes = new LinkedHashMap<>();
    for (List<String> batch : batches(symbols)) {
      String url = DATA + "/snapshots?symbols=" + encode(String.join(",", batch)) + "&feed=delayed_sip";
      JsonNode body;
      try {
        body = http.getJson(url, headers(), JsonNode.class);
      } catch (RuntimeException e) {
        log.warn("Snapshot batch of {} symbols failed: {}", batch.size(), e.getMessage());
        continue;
      }
      for (var entry : body.properties()) {
        toQuote(entry.getValue()).ifPresent(q -> quotes.put(entry.getKey(), q));
      }
    }
    return quotes;
  }

  private static Optional<StockQuote> toQuote(JsonNode snap) {
    JsonNode daily = snap == null ? null : snap.get("dailyBar");
    JsonNode prev = snap == null ? null : snap.get("prevDailyBar");
    JsonNode trade = snap == null ? null : snap.get("latestTrade");
    if (daily == null || daily.isNull() || prev == null || prev.isNull() || trade == null || trade.isNull()) {
      return Optional.empty();
    }
    return Optional.of(new StockQuote(
        daily.get("c").asDouble(),
        prev.get("c").asDouble(),
        daily.get("v").asLong(),
        EtTime.etDate(daily.get("t").asString()),
        trade.get("t").asString()));
  }

  /** Split-adjusted daily closes from {@code start} to {@code end}, oldest first per symbol. */
  public Map<String, List<DailyBar>> getDailyBars(List<String> symbols, LocalDate start, Instant end) {
    Map<String, List<DailyBar>> result = new LinkedHashMap<>();
    for (List<String> batch : batches(symbols)) {
      String pageToken = null;
      do {
        String url = DATA + "/bars?symbols=" + encode(String.join(",", batch))
            + "&timeframe=1Day&start=" + start + "&end=" + encode(end.toString())
            + "&feed=sip&adjustment=split&limit=10000"
            + (pageToken == null ? "" : "&page_token=" + encode(pageToken));
        JsonNode page = http.getJson(url, headers(), JsonNode.class);
        for (var entry : page.get("bars").properties()) {
          List<DailyBar> bars = result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
          for (JsonNode bar : entry.getValue()) {
            bars.add(new DailyBar(EtTime.etDate(bar.get("t").asString()), bar.get("c").asDouble()));
          }
        }
        JsonNode next = page.get("next_page_token");
        pageToken = next == null || next.isNull() ? null : next.asString();
      } while (pageToken != null);
    }
    result.values().forEach(bars -> bars.sort(Comparator.comparing(DailyBar::date)));
    return result;
  }

  public List<String> listActiveSymbols() {
    JsonNode assets = http.getJson(TRADING + "/assets?status=active&asset_class=us_equity", headers(), JsonNode.class);
    List<String> symbols = new ArrayList<>();
    assets.forEach(a -> symbols.add(a.get("symbol").asString()));
    return symbols;
  }

  private Map<String, String> headers() {
    return Map.of(
        "APCA-API-KEY-ID", secrets.get(SecretName.ALPACA_KEY_ID),
        "APCA-API-SECRET-KEY", secrets.get(SecretName.ALPACA_SECRET));
  }

  private static List<List<String>> batches(List<String> symbols) {
    List<List<String>> batches = new ArrayList<>();
    for (int i = 0; i < symbols.size(); i += BATCH) {
      batches.add(symbols.subList(i, Math.min(i + BATCH, symbols.size())));
    }
    return batches;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
