package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EtTimeTest {

  @Test
  void convertsUtcToEasternDate() {
    assertThat(EtTime.etDate("2026-01-02T04:30:00Z")).isEqualTo("2026-01-01");
    assertThat(EtTime.etDate(Instant.parse("2026-07-01T03:59:00Z"))).isEqualTo("2026-06-30");
  }

  @Test
  void formatsEasternClockTime() {
    assertThat(EtTime.formatEtTime("2026-10-02T18:35:00Z")).isEqualTo("14:35 ET");
    assertThat(EtTime.formatEtTime("2026-01-05T14:30:00Z")).isEqualTo("09:30 ET");
  }
}
