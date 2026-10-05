package com.stockheatmap.integrations.watchlist;

import com.stockheatmap.core.model.Watchlist;
import com.stockheatmap.core.model.WatchlistItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * DynamoDB table "Watchlist": partition key userId (the Cognito "sub"); items stored as a JSON
 * string; updatedAt as an ISO timestamp.
 */
public class DynamoWatchlistRepo implements WatchlistRepo {

  private final DynamoDbClient dynamo;
  private final String table;
  private final ObjectMapper mapper;

  public DynamoWatchlistRepo(DynamoDbClient dynamo, String table, ObjectMapper mapper) {
    this.dynamo = dynamo;
    this.table = table;
    this.mapper = mapper;
  }

  @Override
  public Watchlist get(String userId) {
    GetItemResponse response = dynamo.getItem(
        GetItemRequest.builder().tableName(table).key(Map.of("userId", AttributeValue.fromS(userId))).build());
    if (!response.hasItem() || response.item().isEmpty()) {
      return new Watchlist(List.of(), null);
    }
    Map<String, AttributeValue> item = response.item();
    return new Watchlist(WatchlistJson.read(mapper, item.get("items").s()), item.get("updatedAt").s());
  }

  @Override
  public Watchlist put(String userId, List<WatchlistItem> items) {
    String updatedAt = Instant.now().toString();
    dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
        "userId", AttributeValue.fromS(userId),
        "items", AttributeValue.fromS(WatchlistJson.write(mapper, items)),
        "updatedAt", AttributeValue.fromS(updatedAt))).build());
    return new Watchlist(List.copyOf(items), updatedAt);
  }

  @Override
  public List<WatchlistItem> allItems() {
    List<WatchlistItem> all = new ArrayList<>();
    Map<String, AttributeValue> startKey = null;
    do {
      ScanRequest.Builder request = ScanRequest.builder().tableName(table)
          .projectionExpression("#i").expressionAttributeNames(Map.of("#i", "items"));
      if (startKey != null) {
        request.exclusiveStartKey(startKey);
      }
      ScanResponse page = dynamo.scan(request.build());
      page.items().forEach(item -> all.addAll(WatchlistJson.read(mapper, item.get("items").s())));
      startKey = page.hasLastEvaluatedKey() && !page.lastEvaluatedKey().isEmpty() ? page.lastEvaluatedKey() : null;
    } while (startKey != null);
    return all;
  }
}
