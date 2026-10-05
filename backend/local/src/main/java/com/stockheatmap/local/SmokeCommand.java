package com.stockheatmap.local;

import com.stockheatmap.core.Sp500Parser;
import com.stockheatmap.integrations.alpaca.AlpacaClient;
import com.stockheatmap.integrations.coinbase.CoinbaseClient;
import com.stockheatmap.integrations.coingecko.CoinGeckoClient;
import com.stockheatmap.integrations.finnhub.FinnhubClient;
import com.stockheatmap.integrations.wikipedia.WikipediaClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/** {@code --smoke}: one real call per external API, to catch upstream changes the fixtures can't. */
@Component
class SmokeCommand implements ApplicationRunner, ExitCodeGenerator {

  private final Map<String, Supplier<String>> checks = new LinkedHashMap<>();
  private int exitCode;

  SmokeCommand(AlpacaClient alpaca, CoinGeckoClient coingecko, FinnhubClient finnhub, WikipediaClient wikipedia,
      CoinbaseClient coinbase) {
    checks.put("alpaca", () -> "market open=" + alpaca.getClock().isOpen());
    checks.put("coingecko", () -> "top coin=" + coingecko.topMarkets(1).getFirst().id());
    checks.put("finnhub", () -> "AAPL shares=" + finnhub.sharesOutstanding("AAPL").orElseThrow());
    checks.put("wikipedia", () -> {
      var rows = Sp500Parser.parse(wikipedia.fetchSp500Html());
      Sp500Parser.validate(rows);
      return rows.size() + " S&P 500 rows";
    });
    checks.put("coinbase", () -> coinbase.listUsdProducts().size() + " USD products");
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption("smoke")) {
      return;
    }
    checks.forEach((name, check) -> {
      try {
        System.out.println("OK " + name + ": " + check.get());
      } catch (RuntimeException e) {
        System.out.println("FAIL " + name + ": " + e.getMessage());
        exitCode = 1;
      }
    });
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
