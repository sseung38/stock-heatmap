package com.stockheatmap.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.SymbolsFile;
import com.stockheatmap.core.model.Watchlist;
import com.stockheatmap.core.model.WatchlistItem.StockItem;
import com.stockheatmap.integrations.store.MemoryDataStore;
import com.stockheatmap.integrations.watchlist.MemoryWatchlistRepo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WatchlistServiceTest {

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final MemoryDataStore store = new MemoryDataStore(mapper);
  private final MemoryWatchlistRepo repo = new MemoryWatchlistRepo();
  private final WatchlistService service = new WatchlistService(repo, store, mapper);

  @BeforeEach
  void symbolsExist() {
    store.put(DataKey.SYMBOLS, new SymbolsFile("", List.of("AAPL", "PLTR"), List.of("solana")));
  }

  @Test
  void getReturnsEmptyListForNewUser() {
    assertThat(service.handle("GET", "u1", null)).isEqualTo(new ApiResult(200, new Watchlist(List.of(), null)));
  }

  @Test
  void putSavesNormalizedList() {
    ApiResult result = service.handle("PUT", "u1", "{\"items\":[{\"type\":\"stock\",\"symbol\":\"pltr\"}]}");

    assertThat(result.status()).isEqualTo(200);
    assertThat(((Watchlist) result.body()).items()).containsExactly(new StockItem("PLTR"));
    assertThat(repo.get("u1").items()).containsExactly(new StockItem("PLTR"));
  }

  @Test
  void putRejectsUnparseableJson() {
    assertThat(service.handle("PUT", "u1", "{bad"))
        .isEqualTo(new ApiResult(400, new ErrorBody("Invalid request body", List.of())));
  }

  @Test
  void putRejectsUnknownSymbolsAndKeepsOldList() {
    repo.put("u1", List.of(new StockItem("AAPL")));

    ApiResult result = service.handle("PUT", "u1", "{\"items\":[{\"type\":\"stock\",\"symbol\":\"ZZZZ\"}]}");

    assertThat(result).isEqualTo(
        new ApiResult(400, new ErrorBody("Unknown or duplicate items", List.of(new StockItem("ZZZZ")))));
    assertThat(repo.get("u1").items()).containsExactly(new StockItem("AAPL"));
  }

  @Test
  void putReturns503UntilSymbolListExists() {
    WatchlistService noSymbols = new WatchlistService(repo, new MemoryDataStore(mapper), mapper);

    assertThat(noSymbols.handle("PUT", "u1", "{\"items\":[]}"))
        .isEqualTo(new ApiResult(503, new ErrorBody("Symbol list not available yet", List.of())));
  }

  @Test
  void otherMethodsAreNotAllowed() {
    assertThat(service.handle("DELETE", "u1", null).status()).isEqualTo(405);
  }

  @Test
  void errorBodySerializesItemsWithTheirType() {
    String json = mapper.writeValueAsString(new ErrorBody("x", List.of(new StockItem("ZZZZ"))));

    assertThat(json).isEqualTo("{\"error\":\"x\",\"invalid\":[{\"type\":\"stock\",\"symbol\":\"ZZZZ\"}]}");
  }
}
