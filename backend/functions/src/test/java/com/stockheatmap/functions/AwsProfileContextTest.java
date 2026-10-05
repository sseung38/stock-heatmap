package com.stockheatmap.functions;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockheatmap.integrations.secrets.SsmSecretsProvider;
import com.stockheatmap.integrations.store.DataStore;
import com.stockheatmap.integrations.store.S3DataStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.test.context.ActiveProfiles;

/**
 * Starts the context exactly as Lambda does (profile "aws", JobsConfiguration + Spring Cloud
 * Function auto-configuration together). Building AWS SDK clients makes no network calls, so this
 * runs offline; it catches wiring errors that would otherwise only show up as a failed deploy.
 */
@SpringBootTest(classes = FunctionsApplication.class, properties = {"DATA_BUCKET=test-bucket", "WATCHLIST_TABLE=Watchlist"})
@ActiveProfiles("aws")
class AwsProfileContextTest {

  @BeforeAll
  static void region() {
    System.setProperty("aws.region", "us-east-1");
  }

  @Autowired private DataStore dataStore;
  @Autowired private SsmSecretsProvider secrets;
  @Autowired private FunctionCatalog catalog;

  @Test
  void wiresAwsAdaptersAndEveryFunction() {
    assertThat(dataStore).isInstanceOf(S3DataStore.class);
    assertThat(secrets).isNotNull();
    for (String name : new String[] {"refreshStocks", "refreshCrypto", "refreshBaselines", "refreshMetadata"}) {
      assertThat((Object) catalog.lookup(name)).as(name).isNotNull();
    }
  }
}
