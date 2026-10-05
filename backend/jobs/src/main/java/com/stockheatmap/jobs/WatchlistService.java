package com.stockheatmap.jobs;

import com.stockheatmap.core.ValidationResult;
import com.stockheatmap.core.WatchlistValidator;
import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.SymbolsFile;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.watchlist.WatchlistRepo;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** GET / PUT /watchlist for one signed-in user. Used by the Lambda and by the local mock API. */
public class WatchlistService {

  private final WatchlistRepo repo;
  private final DataStore store;
  private final ObjectMapper mapper;

  public WatchlistService(WatchlistRepo repo, DataStore store, ObjectMapper mapper) {
    this.repo = repo;
    this.store = store;
    this.mapper = mapper;
  }

  public ApiResult handle(String method, String userId, String body) {
    return switch (method) {
      case "GET" -> new ApiResult(200, repo.get(userId));
      case "PUT" -> put(userId, body);
      default -> new ApiResult(405, new ErrorBody("Method not allowed", List.of()));
    };
  }

  private ApiResult put(String userId, String body) {
    JsonNode json;
    try {
      json = body == null ? null : mapper.readTree(body);
    } catch (JacksonException e) {
      return new ApiResult(400, new ErrorBody("Invalid request body", List.of()));
    }
    Optional<SymbolsFile> symbols = store.get(DataKey.SYMBOLS, SymbolsFile.class);
    if (symbols.isEmpty()) {
      return new ApiResult(503, new ErrorBody("Symbol list not available yet", List.of()));
    }
    return switch (WatchlistValidator.validate(json, symbols.get())) {
      case ValidationResult.Valid valid -> new ApiResult(200, repo.put(userId, valid.items()));
      case ValidationResult.Invalid invalid -> new ApiResult(400, new ErrorBody(invalid.error(), invalid.invalid()));
    };
  }
}
