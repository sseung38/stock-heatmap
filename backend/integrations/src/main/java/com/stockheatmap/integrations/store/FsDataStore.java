package com.stockheatmap.integrations.store;

import com.stockheatmap.core.model.DataKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/** Files in a local directory (default .data/), for running everything on your machine. */
public class FsDataStore implements DataStore {

  private final Path dir;
  private final ObjectMapper mapper;

  public FsDataStore(Path dir, ObjectMapper mapper) {
    this.dir = dir;
    this.mapper = mapper;
  }

  @Override
  public <T> Optional<T> get(DataKey key, Class<T> type) {
    Path file = dir.resolve(key.key());
    return Files.exists(file) ? Optional.of(mapper.readValue(file.toFile(), type)) : Optional.empty();
  }

  /** Writes to a temp file then renames, so a crash never leaves a half-written file behind. */
  @Override
  public void put(DataKey key, Object value) {
    try {
      Files.createDirectories(dir);
      Path tmp = Files.createTempFile(dir, key.key(), ".tmp");
      mapper.writeValue(tmp.toFile(), value);
      Files.move(tmp, dir.resolve(key.key()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
