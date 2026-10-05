package com.stockheatmap.integrations.http;

/** Pauses between retries and rate-limited calls; tests pass a recorder instead of really sleeping. */
@FunctionalInterface
public interface Sleeper {

  void sleep(long millis);

  Sleeper REAL = millis -> {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while sleeping", e);
    }
  };
}
