package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergeTest {

  record Value(int v, String asOf) {}

  private static final List<String> UNIVERSE = List.of("AAPL", "MSFT", "NVDA");

  @Test
  void carriesPreviousValueForTickerMissingFromFresh() {
    Map<String, Value> previous = Map.of("NVDA", new Value(1, "old"));
    Map<String, Value> fresh = Map.of("AAPL", new Value(2, "new"), "MSFT", new Value(3, "new"));

    MergeResult<Value> result = Merge.snapshot(previous, fresh, UNIVERSE);

    assertThat(result.merged())
        .containsEntry("NVDA", new Value(1, "old"))
        .containsEntry("AAPL", new Value(2, "new"))
        .containsEntry("MSFT", new Value(3, "new"));
    assertThat(result.freshRatio()).isCloseTo(2.0 / 3, within(1e-9));
  }

  @Test
  void dropsTickersOutsideTheUniverse() {
    MergeResult<Value> result =
        Merge.snapshot(Map.of("OLD", new Value(1, "old")), Map.of("GONE", new Value(2, "new")), UNIVERSE);

    assertThat(result.merged()).doesNotContainKeys("OLD", "GONE");
    assertThat(result.freshRatio()).isZero();
  }

  @Test
  void handlesNoPreviousFile() {
    MergeResult<Value> result = Merge.snapshot(null, Map.of("AAPL", new Value(2, "new")), UNIVERSE);

    assertThat(result.merged()).containsOnlyKeys("AAPL");
  }

  @Test
  void treatsEmptyUniverseAsNothingFresh() {
    assertThat(Merge.snapshot(null, Map.of(), List.of()).freshRatio()).isZero();
  }

  @Test
  void assertFreshRejectsBelowNinetyPercent() {
    assertThatThrownBy(() -> Freshness.assertFresh(0.89, "stocks"))
        .isInstanceOf(StaleDataException.class)
        .hasMessageContaining("stocks");
    assertThatCode(() -> Freshness.assertFresh(0.9, "stocks")).doesNotThrowAnyException();
  }
}
