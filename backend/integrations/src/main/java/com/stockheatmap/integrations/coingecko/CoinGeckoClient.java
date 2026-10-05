package com.stockheatmap.integrations.coingecko;

import com.stockheatmap.core.model.CoinGeckoMarket;
import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.secrets.SecretName;
import com.stockheatmap.integrations.secrets.SecretsProvider;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.JsonNode;

/** CoinGecko Demo API (free; 10,000 calls/month). */
public class CoinGeckoClient {

  static final String BASE = "https://api.coingecko.com/api/v3";
  private static final String MARKETS_QUERY =
      "/coins/markets?vs_currency=usd&order=market_cap_desc&sparkline=true&price_change_percentage=24h,7d,30d";
  private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
  private static final ParameterizedTypeReference<List<CoinGeckoMarket>> MARKETS = new ParameterizedTypeReference<>() {};

  private final RetryingHttp http;
  private final SecretsProvider secrets;

  public CoinGeckoClient(RetryingHttp http, SecretsProvider secrets) {
    this.http = http;
    this.secrets = secrets;
  }

  public List<CoinGeckoMarket> topMarkets(int n) {
    return http.getJson(BASE + MARKETS_QUERY + "&per_page=" + n + "&page=1", headers(), MARKETS);
  }

  /** Coin ids are URL-safe slugs such as "solana", so they are joined as-is. */
  public List<CoinGeckoMarket> marketsByIds(List<String> ids) {
    return http.getJson(BASE + MARKETS_QUERY + "&ids=" + String.join(",", ids), headers(), MARKETS);
  }

  public OptionalDouble priceOn(String id, LocalDate date) {
    JsonNode body = http.getJson(
        BASE + "/coins/" + id + "/history?date=" + HISTORY_DATE.format(date) + "&localization=false",
        headers(), JsonNode.class);
    JsonNode usd = body.path("market_data").path("current_price").path("usd");
    return usd.isNumber() ? OptionalDouble.of(usd.asDouble()) : OptionalDouble.empty();
  }

  public List<String> listCoinIds() {
    JsonNode coins = http.getJson(BASE + "/coins/list", headers(), JsonNode.class);
    List<String> ids = new ArrayList<>();
    coins.forEach(c -> ids.add(c.get("id").asString()));
    return ids;
  }

  private Map<String, String> headers() {
    return Map.of("x-cg-demo-api-key", secrets.get(SecretName.COINGECKO_KEY));
  }
}
