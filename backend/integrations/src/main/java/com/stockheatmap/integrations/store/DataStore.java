package com.stockheatmap.integrations.store;

import com.stockheatmap.core.model.DataKey;
import java.util.Optional;

/** Where the JSON snapshots live: S3 on AWS, the .data directory locally, memory in tests. */
public interface DataStore {

  <T> Optional<T> get(DataKey key, Class<T> type);

  void put(DataKey key, Object value);
}
