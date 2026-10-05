package com.stockheatmap.integrations.store;

import com.stockheatmap.core.model.DataKey;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/** In-memory store for tests. Keeps JSON strings so serialization is exercised for real. */
public class MemoryDataStore implements DataStore {

  private final Map<DataKey, String> files = new EnumMap<>(DataKey.class);
  private final ObjectMapper mapper;

  public MemoryDataStore(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public <T> Optional<T> get(DataKey key, Class<T> type) {
    return raw(key).map(json -> mapper.readValue(json, type));
  }

  @Override
  public void put(DataKey key, Object value) {
    files.put(key, mapper.writeValueAsString(value));
  }

  public Optional<String> raw(DataKey key) {
    return Optional.ofNullable(files.get(key));
  }
}
