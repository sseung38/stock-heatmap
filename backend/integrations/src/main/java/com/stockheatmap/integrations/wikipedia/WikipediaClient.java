package com.stockheatmap.integrations.wikipedia;

import com.stockheatmap.integrations.http.RetryingHttp;
import java.util.Map;

public class WikipediaClient {

  static final String SP500_PAGE = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies";

  /** Wikimedia asks automated clients to identify themselves. */
  static final String USER_AGENT = "stock-heatmap/1.0 (personal project)";

  private final RetryingHttp http;

  public WikipediaClient(RetryingHttp http) {
    this.http = http;
  }

  public String fetchSp500Html() {
    return http.getText(SP500_PAGE, Map.of("User-Agent", USER_AGENT));
  }
}
