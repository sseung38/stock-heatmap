package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockheatmap.core.model.MetadataFile;
import com.stockheatmap.core.model.Sp500Entry;
import com.stockheatmap.core.model.WatchlistItem.CryptoItem;
import com.stockheatmap.core.model.WatchlistItem.StockItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UniverseTest {

  private static final MetadataFile META =
      new MetadataFile(
          "",
          List.of(new Sp500Entry("MSFT", "Microsoft", "Information Technology"),
              new Sp500Entry("BRK.B", "Berkshire Hathaway", "Financials")),
          Map.of());

  @Test
  void stockUniverseIsSp500PlusWatchlistStocksSortedAndDistinct() {
    List<String> universe =
        Universe.stocks(META, List.of(new StockItem("PLTR"), new StockItem("MSFT"), new CryptoItem("bitcoin", "BTC")));

    assertThat(universe).containsExactly("BRK.B", "MSFT", "PLTR");
  }

  @Test
  void stockUniverseIsEmptyWithoutMetadataOrWatchlist() {
    assertThat(Universe.stocks(null, List.of())).isEmpty();
  }

  @Test
  void watchlistCoinIdsListsOnlyCrypto() {
    assertThat(Universe.watchlistCoinIds(List.of(new StockItem("PLTR"), new CryptoItem("solana", "SOL"))))
        .containsExactly("solana");
  }
}
