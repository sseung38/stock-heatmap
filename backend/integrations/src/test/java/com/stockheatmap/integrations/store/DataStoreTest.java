package com.stockheatmap.integrations.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockheatmap.core.model.DataKey;
import com.stockheatmap.core.model.Market;
import com.stockheatmap.core.model.StockQuote;
import com.stockheatmap.core.model.StocksFile;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DataStoreTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final StocksFile stocks = new StocksFile("2026-10-02T18:35:00Z", new Market(true),
      Map.of("AAPL", new StockQuote(227.52, 225.1, 100, "2026-10-02", "2026-10-02T18:20:01Z")));

  @Test
  void fsStoreRoundTripsAndReturnsEmptyForMissingFile(@TempDir Path dir) {
    FsDataStore store = new FsDataStore(dir.resolve(".data"), mapper);

    assertThat(store.get(DataKey.STOCKS, StocksFile.class)).isEmpty();
    store.put(DataKey.STOCKS, stocks);

    assertThat(store.get(DataKey.STOCKS, StocksFile.class)).contains(stocks);
    assertThat(dir.resolve(".data/stocks.json")).exists();
  }

  @Test
  void memoryStoreKeepsRealJson() {
    MemoryDataStore store = new MemoryDataStore(mapper);
    store.put(DataKey.STOCKS, stocks);

    assertThat(store.raw(DataKey.STOCKS)).get().asString().contains("\"isOpen\":true");
    assertThat(store.get(DataKey.STOCKS, StocksFile.class)).contains(stocks);
    assertThat(store.get(DataKey.CRYPTO, StocksFile.class)).isEmpty();
  }

  @Test
  void s3StoreReturnsEmptyForMissingObject() {
    S3Client s3 = mock(S3Client.class);
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

    assertThat(new S3DataStore(s3, "bucket", mapper).get(DataKey.STOCKS, StocksFile.class)).isEmpty();
  }

  @Test
  void s3StoreReadsJson() {
    S3Client s3 = mock(S3Client.class);
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), mapper.writeValueAsBytes(stocks)));

    assertThat(new S3DataStore(s3, "bucket", mapper).get(DataKey.STOCKS, StocksFile.class)).contains(stocks);
  }

  @Test
  void s3StoreWritesJsonWithContentType() {
    S3Client s3 = mock(S3Client.class);

    new S3DataStore(s3, "bucket", mapper).put(DataKey.STOCKS, stocks);

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("bucket");
    assertThat(request.getValue().key()).isEqualTo("stocks.json");
    assertThat(request.getValue().contentType()).isEqualTo("application/json");
  }
}
