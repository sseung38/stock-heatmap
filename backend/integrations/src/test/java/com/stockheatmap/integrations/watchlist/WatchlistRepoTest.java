package com.stockheatmap.integrations.watchlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockheatmap.core.model.Watchlist;
import com.stockheatmap.core.model.WatchlistItem;
import com.stockheatmap.core.model.WatchlistItem.CryptoItem;
import com.stockheatmap.core.model.WatchlistItem.StockItem;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WatchlistRepoTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final List<WatchlistItem> items = List.of(new StockItem("PLTR"), new CryptoItem("solana", "SOL"));

  @Test
  void fsRepoStoresPerUserAndListsAllItems(@TempDir Path dir) {
    FsWatchlistRepo repo = new FsWatchlistRepo(dir.resolve("watchlist.json"), mapper);

    assertThat(repo.get("u1")).isEqualTo(new Watchlist(List.of(), null));
    Watchlist saved = repo.put("u1", items);
    repo.put("u2", List.of(new StockItem("NVDA")));

    assertThat(saved.updatedAt()).isNotNull();
    assertThat(repo.get("u1").items()).isEqualTo(items);
    assertThat(repo.allItems()).containsExactlyInAnyOrder(
        new StockItem("PLTR"), new CryptoItem("solana", "SOL"), new StockItem("NVDA"));
  }

  @Test
  void dynamoRepoReturnsEmptyWatchlistForNewUser() {
    DynamoDbClient dynamo = mock(DynamoDbClient.class);
    when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

    assertThat(new DynamoWatchlistRepo(dynamo, "Watchlist", mapper).get("u1")).isEqualTo(new Watchlist(List.of(), null));
  }

  @Test
  void dynamoRepoWritesItemsAsJsonAndReadsThemBack() {
    DynamoDbClient dynamo = mock(DynamoDbClient.class);
    DynamoWatchlistRepo repo = new DynamoWatchlistRepo(dynamo, "Watchlist", mapper);

    Watchlist saved = repo.put("u1", items);

    ArgumentCaptor<PutItemRequest> put = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(dynamo).putItem(put.capture());
    Map<String, AttributeValue> item = put.getValue().item();
    assertThat(put.getValue().tableName()).isEqualTo("Watchlist");
    assertThat(item.get("userId").s()).isEqualTo("u1");
    assertThat(item.get("updatedAt").s()).isEqualTo(saved.updatedAt());

    when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(item).build());
    assertThat(repo.get("u1")).isEqualTo(saved);
  }

  @Test
  void dynamoRepoFlattensItemsOfAllUsers() {
    DynamoDbClient dynamo = mock(DynamoDbClient.class);
    when(dynamo.scan(any(ScanRequest.class))).thenReturn(ScanResponse.builder().items(
        Map.of("items", AttributeValue.fromS("[{\"type\":\"stock\",\"symbol\":\"PLTR\"}]")),
        Map.of("items", AttributeValue.fromS("[{\"type\":\"crypto\",\"id\":\"solana\",\"symbol\":\"SOL\"}]"))).build());

    assertThat(new DynamoWatchlistRepo(dynamo, "Watchlist", mapper).allItems())
        .containsExactly(new StockItem("PLTR"), new CryptoItem("solana", "SOL"));
  }
}
