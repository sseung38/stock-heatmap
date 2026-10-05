package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockheatmap.core.ValidationResult.Invalid;
import com.stockheatmap.core.ValidationResult.Valid;
import com.stockheatmap.core.model.SymbolsFile;
import com.stockheatmap.core.model.WatchlistItem.CryptoItem;
import com.stockheatmap.core.model.WatchlistItem.StockItem;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class WatchlistValidatorTest {

  private static final SymbolsFile SYMBOLS =
      new SymbolsFile("", List.of("AAPL", "BRK.B"), List.of("solana"));

  private static ValidationResult validate(String json) {
    return WatchlistValidator.validate(JsonMapper.builder().build().readTree(json), SYMBOLS);
  }

  @Test
  void normalizesCaseAndWhitespace() {
    assertThat(validate("""
        {"items":[{"type":"stock","symbol":" aapl "},{"type":"crypto","id":"Solana","symbol":"sol"}]}"""))
        .isEqualTo(new Valid(List.of(new StockItem("AAPL"), new CryptoItem("solana", "SOL"))));
  }

  @Test
  void acceptsShareClassTickers() {
    assertThat(validate("""
        {"items":[{"type":"stock","symbol":"brk.b"}]}"""))
        .isEqualTo(new Valid(List.of(new StockItem("BRK.B"))));
  }

  @Test
  void acceptsEmptyList() {
    assertThat(validate("{\"items\":[]}")).isEqualTo(new Valid(List.of()));
  }

  @Test
  void rejectsUnknownAndDuplicateItems() {
    assertThat(validate("""
        {"items":[{"type":"stock","symbol":"AAPL"},{"type":"stock","symbol":"ZZZZ"},{"type":"stock","symbol":"aapl"}]}"""))
        .isEqualTo(new Invalid("Unknown or duplicate items", List.of(new StockItem("ZZZZ"), new StockItem("AAPL"))));
  }

  @Test
  void rejectsUnknownCoinId() {
    assertThat(validate("""
        {"items":[{"type":"crypto","id":"notacoin","symbol":"NOPE"}]}"""))
        .isEqualTo(new Invalid("Unknown or duplicate items", List.of(new CryptoItem("notacoin", "NOPE"))));
  }

  @Test
  void capsAtOneHundredItems() {
    String items =
        IntStream.range(0, 101).mapToObj(i -> "{\"type\":\"stock\",\"symbol\":\"AAPL\"}").collect(Collectors.joining(","));

    assertThat(validate("{\"items\":[" + items + "]}"))
        .isEqualTo(new Invalid("Watchlist can hold at most 100 items", List.of()));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "null",
    "{}",
    "{\"items\":\"x\"}",
    "{\"items\":[{\"type\":\"etf\",\"symbol\":\"SPY\"}]}",
    "{\"items\":[{\"type\":\"stock\",\"symbol\":\"  \"}]}",
    "{\"items\":[{\"type\":\"crypto\",\"symbol\":\"SOL\"}]}"
  })
  void rejectsMalformedBodies(String json) {
    assertThat(validate(json)).isEqualTo(new Invalid("Invalid request body", List.of()));
  }

  @Test
  void rejectsMissingBody() {
    assertThat(WatchlistValidator.validate(null, SYMBOLS)).isEqualTo(new Invalid("Invalid request body", List.of()));
  }
}
