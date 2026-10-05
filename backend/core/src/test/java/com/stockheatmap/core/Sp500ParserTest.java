package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockheatmap.core.model.Sp500Entry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runs against a real copy of the Wikipedia page saved in test resources. */
class Sp500ParserTest {

  private static List<Sp500Entry> rows;

  @BeforeAll
  static void parseFixture() throws IOException {
    try (InputStream in = Sp500ParserTest.class.getResourceAsStream("/fixtures/wikipedia-sp500.html")) {
      rows = Sp500Parser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  void parsesTheConstituentsTable() {
    assertThat(rows).hasSizeGreaterThanOrEqualTo(495);
    assertThat(rows).contains(new Sp500Entry("AAPL", "Apple Inc.", "Information Technology"));
    assertThatCode(() -> Sp500Parser.validate(rows)).doesNotThrowAnyException();
  }

  @Test
  void keepsShareClassTickers() {
    assertThat(rows).extracting(Sp500Entry::symbol).contains("BRK.B");
  }

  @Test
  void rejectsTooFewRows() {
    assertThatThrownBy(() -> Sp500Parser.validate(rows.subList(0, 400))).isInstanceOf(InvalidScrapeException.class);
  }

  @Test
  void rejectsMissingSector() {
    List<Sp500Entry> noUtilities = rows.stream().filter(r -> !r.sector().equals("Utilities")).toList();

    assertThatThrownBy(() -> Sp500Parser.validate(noUtilities)).isInstanceOf(InvalidScrapeException.class);
  }

  @Test
  void returnsNothingForPageWithoutTable() {
    assertThat(Sp500Parser.parse("<html></html>")).isEmpty();
  }
}
