package com.stockheatmap.integrations.secrets;

/** API keys: SSM parameter name in AWS, environment variable name locally (.env). */
public enum SecretName {
  ALPACA_KEY_ID("/stock-heatmap/alpaca-key-id", "ALPACA_KEY_ID"),
  ALPACA_SECRET("/stock-heatmap/alpaca-secret", "ALPACA_SECRET"),
  FINNHUB_KEY("/stock-heatmap/finnhub-key", "FINNHUB_KEY"),
  COINGECKO_KEY("/stock-heatmap/coingecko-key", "COINGECKO_KEY");

  private final String ssmName;
  private final String envName;

  SecretName(String ssmName, String envName) {
    this.ssmName = ssmName;
    this.envName = envName;
  }

  public String ssmName() {
    return ssmName;
  }

  public String envName() {
    return envName;
  }
}
