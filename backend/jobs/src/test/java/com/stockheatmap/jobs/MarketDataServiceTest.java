package com.stockheatmap.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.Market;
import com.stockheatmap.core.model.MarketData;
import com.stockheatmap.core.model.StocksFile;
import com.stockheatmap.integrations.store.MemoryDataStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MarketDataServiceTest {

  private final MemoryDataStore store = new MemoryDataStore(JsonMapper.builder().build());
  private final MarketDataService service = new MarketDataService(store);

  @Test
  void returnsNullsBeforeAnyRefreshHasRun() {
    assertThat(service.load()).isEqualTo(new MarketData(null, null, null, null));
  }

  @Test
  void returnsWhateverFilesExist() {
    StocksFile stocks = new StocksFile("t", new Market(false), Map.of());
    store.put(DataKey.STOCKS, stocks);

    MarketData data = service.load();

    assertThat(data.stocks()).isEqualTo(stocks);
    assertThat(data.crypto()).isNull();
  }
}
