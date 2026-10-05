package com.stockheatmap.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The same JSON samples in /contract are type-checked by the TypeScript tests.
 * If either side renames or drops a field, one of the two suites fails.
 */
class ContractTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @ParameterizedTest
  @CsvSource({
    "stocks.json,StocksFile",
    "crypto.json,CryptoFile",
    "baselines.json,BaselinesFile",
    "metadata.json,MetadataFile",
    "watchlist.json,Watchlist"
  })
  void roundTripsContractSamples(String file, String type) throws Exception {
    JsonNode original = mapper.readTree(Path.of("../../contract", file).toFile());

    Object parsed = mapper.treeToValue(original, Class.forName("com.stockheatmap.core.model." + type));

    // Compare as re-parsed JSON text: a tree built from a Java `long` holds a LongNode while the
    // parsed sample holds an IntNode for the same number, and JsonNode.equals() tells them apart.
    JsonNode reserialized = mapper.readTree(mapper.writeValueAsString(parsed));
    assertThat(reserialized).isEqualTo(original);
  }
}
