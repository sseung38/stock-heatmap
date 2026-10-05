package com.stockheatmap.integrations.finnhub;

import com.stockheatmap.integrations.http.RetryingHttp;
import com.stockheatmap.integrations.secrets.SecretName;
import com.stockheatmap.integrations.secrets.SecretsProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.OptionalDouble;
import tools.jackson.databind.JsonNode;

/** Finnhub free API (60 calls/min): shares outstanding, used to size S&P 500 tiles by market cap. */
public class FinnhubClient {

  static final String PROFILE = "https://finnhub.io/api/v1/stock/profile2";

  private final RetryingHttp http;
  private final SecretsProvider secrets;

  public FinnhubClient(RetryingHttp http, SecretsProvider secrets) {
    this.http = http;
    this.secrets = secrets;
  }

  /** Absolute share count (Finnhub reports millions); empty when Finnhub has no profile. */
  public OptionalDouble sharesOutstanding(String symbol) {
    String url = PROFILE + "?symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
        + "&token=" + secrets.get(SecretName.FINNHUB_KEY);
    JsonNode shares = http.getJson(url, Map.of(), JsonNode.class).path("shareOutstanding");
    return shares.isNumber() ? OptionalDouble.of(shares.asDouble() * 1e6) : OptionalDouble.empty();
  }
}
