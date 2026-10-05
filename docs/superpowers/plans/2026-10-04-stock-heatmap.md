# Stock Heatmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a hosted, single-user stock/crypto treemap heatmap (S&P 500, Watchlist, Crypto) on AWS serverless at $0/month, with a Java + Spring Boot backend, deployed by GitHub Actions.

**Architecture:** Scheduled Spring Cloud Function Lambdas fetch market data from free APIs and write JSON snapshots to a private S3 bucket; a Cognito-protected HTTP API (also Spring Cloud Function Lambdas) serves the merged snapshot and the watchlist (DynamoDB) to a React SPA on CloudFront; the browser streams live crypto prices straight from Coinbase. Business logic is plain Java shared by the Lambdas and a local Spring Boot app.

**Tech Stack:** Backend — Java 21, Spring Boot 4.1.x, Spring Cloud 2025.1.x (Spring Cloud Function AWS adapter), Gradle Kotlin DSL (wrapper), AWS SDK for Java v2, Spring `RestClient`, jsoup, JUnit 5 + AssertJ + Mockito. Web — React 19 + Vite, TypeScript, d3-hierarchy, TanStack Query 5, react-oidc-context 3, Vitest. Infra — AWS CDK v2 (TypeScript). CI/CD — GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-04-stock-heatmap-design.md` — §14 overrides earlier sections (§14.6 = Spring backend).

## Global Constraints

- Java 21 (Temurin), Spring Boot 4.1.x with the Spring Cloud 2025.1.x BOM (≥ 2025.1.2). Base package `com.stockheatmap`. Jackson 3 (`tools.jackson.databind`), annotations from `com.fasterxml.jackson.annotation`.
- Gradle via wrapper only (`backend/gradlew`); Kotlin DSL; modules `core`, `integrations`, `jobs`, `functions`, `local`. `core` has no Spring dependency.
- Node.js 22 for `shared`, `web`, `infra` (npm workspaces `@sh/shared`, `@sh/web`, `@sh/infra`); TypeScript `strict`.
- Lambda: runtime `java21`, `arm64`, 1024 MB, SnapStart `ON_PUBLISHED_VERSIONS`, alias `live`, handler `org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest`, env `MAIN_CLASS=com.stockheatmap.functions.FunctionsApplication` and `spring_cloud_function_definition=<bean>`. Package: `backend/functions/build/distributions/functions-lambda.zip` (classes at root, dependencies in `lib/`).
- Function bean names exactly: `refreshStocks`, `refreshCrypto`, `refreshBaselines`, `refreshMetadata`, `getMarketData`, `watchlist`. Lambda names `stock-heatmap-refresh-stocks`, `-refresh-crypto`, `-refresh-baselines`, `-refresh-metadata`, `-get-market-data`, `-watchlist`.
- Spring profiles: `aws` (Lambda) and `local` (dev). Secrets load lazily on first use, never at startup.
- AWS region `us-east-1`. Stack names `StockHeatmapStack`, `StockHeatmapGithubOidc`.
- $0/month: no NAT, no Secrets Manager, no provisioned concurrency; DynamoDB provisioned 1 RCU / 1 WCU.
- Percentages stored as **fractions** (2.5% → `0.025`); CoinGecko percents ÷ 100 at the client boundary.
- Timestamps ISO-8601 UTC; displayed in `America/New_York` as `HH:MM ET`.
- Data bucket keys exactly: `stocks.json`, `crypto.json`, `baselines.json`, `metadata.json`, `symbols.json`.
- SSM parameters exactly: `/stock-heatmap/alpaca-key-id`, `/stock-heatmap/alpaca-secret`, `/stock-heatmap/finnhub-key`, `/stock-heatmap/coingecko-key`. Local `.env` keys: `ALPACA_KEY_ID`, `ALPACA_SECRET`, `FINNHUB_KEY`, `COINGECKO_KEY`.
- Never commit secrets: `.env`, `.data/`, `cdk.out/`, `cdk-outputs.json`, `web/config.remote.json`, `backend/**/build/`, `.gradle/` are git-ignored.
- External calls retry 3× (delays 500, 1000, 2000 ms) on 429/5xx/IO errors.
- `MIN_FRESH_RATIO = 0.9`.
- JSON field names follow spec §4.2/§14.3 exactly (camelCase); `WatchlistItem` is discriminated by `"type": "stock" | "crypto"`.
- Exact UI copy: `Crypto data by CoinGecko`, `Stock data delayed since HH:MM ET`, `Market closed · as of HH:MM ET`, `Stocks as of HH:MM ET`, `Retrying…`, `Crypto live ●`, `Reconnecting…`, `delayed`, `n/a`, `Waiting for first data refresh`.
- Colors: red `#e5484d` ← gray `#3b3f4a` → green `#30a46c`; clamps 1D ±0.03, 1W ±0.06, 1M ±0.10, YTD ±0.20; null `#2a2d35`.
- GitHub repo public; CI job id `ci`.

## Review Focus

1. **Share-class tickers (`BRK.B`, `BF.B`)** must survive Wikipedia parsing, Alpaca snapshots and watchlist validation unchanged. Tests: Tasks 3, 4, 5.
2. **Messy watchlist input (`" aapl "`, `"Solana"`)** is normalized, not rejected. Test: Task 3.
3. **Missing shares outstanding / market cap** — tile still renders at the smallest size. Test: Task 13.
4. **Early January / short history** — YTD uses previous Dec 31 close; < 21 sessions → `n/a` for 1M. Tests: Tasks 1, 12.
5. **First run / empty state, junk messages, Java↔TS drift** — no data files, empty watchlist, non-ticker Coinbase messages, or a renamed JSON field must not crash anything silently. Tests: Tasks 1 and 12 (contract), 8, 14, 15.

## Prerequisites (human)

- Before Task 1: `brew install --cask temurin@21`; `java -version` shows 21.
- Before Task 5: Alpaca account (paper is enough) key id + secret; Finnhub free key; CoinGecko Demo key; copy `.env.example` → `.env` and fill in.
- Before Task 16: AWS CLI v2 configured for `us-east-1` with an admin-capable profile.
- Before Task 22: GitHub account and `gh auth login`.

## File Structure

```
package.json  tsconfig.base.json  eslint.config.js  .gitignore  .env.example
contract/                stocks.json crypto.json baselines.json metadata.json watchlist.json
backend/  settings.gradle.kts  build.gradle.kts  gradlew  gradle/
  core/src/main/java/com/stockheatmap/core/
    model/               StockQuote Market StocksFile CoinQuote CryptoFile StockBaseline CoinBaseline
                         BaselinesFile Sp500Entry MetadataFile SymbolsFile WatchlistItem Watchlist
                         MarketData DataKey CoinGeckoMarket DailyBar
    EtTime Baselines Merge MergeResult Freshness StaleDataException Universe WatchlistValidator
    ValidationResult Sp500Parser InvalidScrapeException CryptoMapping
  integrations/src/main/java/com/stockheatmap/integrations/
    http/                RetryingHttp HttpStatusException Sleeper
    alpaca/ coingecko/ coinbase/ finnhub/ wikipedia/   *Client
    store/               DataStore FsDataStore MemoryDataStore S3DataStore
    watchlist/           WatchlistRepo FsWatchlistRepo MemoryWatchlistRepo DynamoWatchlistRepo
    secrets/             SecretName SecretsProvider EnvSecretsProvider SsmSecretsProvider
  jobs/src/main/java/com/stockheatmap/jobs/
    RefreshStocksJob RefreshCryptoJob RefreshBaselinesJob RefreshMetadataJob RefreshSummary
    MarketDataService WatchlistService ApiResult JobsConfiguration
  functions/src/main/java/com/stockheatmap/functions/
    FunctionsApplication AwsConfiguration LambdaFunctions ApiResponses
  local/src/main/java/com/stockheatmap/local/
    LocalApplication LocalConfiguration MockApiController RefreshCommand SmokeCommand
  */src/test/...         (mirrors main) + resources/fixtures/
shared/src/              index.ts types.ts time.ts  (test: contract.test.ts, time.test.ts)
web/src/                 main.tsx App.tsx config.ts styles.css
  api/                   client.ts ApiProvider.tsx useMarketData.ts
  auth/                  AuthGate.tsx
  heatmap/               change.ts color.ts layoutTreemap.ts buildTiles.ts Heatmap.tsx
  detail/                TileDetail.tsx Sparkline.tsx
  status/                marketStatus.ts StatusBar.tsx
  crypto/                ticker.ts useCryptoStream.ts
  watchlist/             useWatchlist.ts WatchlistEditor.tsx
infra/bin/app.ts  infra/lib/{stack,lambda,data,jobs,api,site,monitoring,github-oidc-stack}.ts  infra/test/
scripts/post-deploy-check.sh
.github/workflows/ci.yml
```

---

## Phase 1 — Spring backend core, clients and local runner (spec §13 step 1)

### Task 1: Gradle multi-project, JSON model, contract samples, stock baselines

**Files:**
- Create: `.gitignore`, `.env.example`, `contract/*.json` (5 hand-written samples matching spec §4.2/§14.3: two stocks incl. `BRK.B`; two coins incl. one with `coinbaseProductId: null` and a `null` change; baselines; metadata; watchlist with one stock and one crypto item), `backend/settings.gradle.kts`, `backend/build.gradle.kts`, `backend/{core,integrations,jobs,functions,local}/build.gradle.kts`, `backend/core/src/main/java/com/stockheatmap/core/model/*.java`, `.../core/EtTime.java`, `.../core/Baselines.java`
- Test: `backend/core/src/test/java/com/stockheatmap/core/{ContractTest,EtTimeTest,BaselinesTest}.java`

**Interfaces:**
- Produces (records, `com.stockheatmap.core.model`):
  ```java
  record StockQuote(double price, double prevClose, long volume, String tradingDay, String asOf) {}
  record Market(@JsonProperty("isOpen") boolean isOpen) {}
  record StocksFile(String asOf, Market market, Map<String, StockQuote> quotes) {}
  record CoinQuote(String symbol, String name, double price, Double change24h, Double change7d, Double change30d,
                   double marketCap, List<Double> sparkline7d, String coinbaseProductId, String asOf) {}
  record CryptoFile(String asOf, Map<String, CoinQuote> coins) {}
  record StockBaseline(List<Double> closes, String lastCloseDate, Map<String, Double> yearEndCloses) {}
  record CoinBaseline(double priceJan1, int year) {}
  record BaselinesFile(String asOf, Map<String, StockBaseline> stocks, Map<String, CoinBaseline> crypto) {}
  record Sp500Entry(String symbol, String name, String sector) {}
  record MetadataFile(String asOf, List<Sp500Entry> sp500, Map<String, Double> sharesOutstanding) {}
  record SymbolsFile(String asOf, List<String> validStockSymbols, List<String> validCoinIds) {}
  @JsonTypeInfo(use = NAME, property = "type")
  @JsonSubTypes({@Type(value = StockItem.class, name = "stock"), @Type(value = CryptoItem.class, name = "crypto")})
  sealed interface WatchlistItem {
    record StockItem(String symbol) implements WatchlistItem {}
    record CryptoItem(String id, String symbol) implements WatchlistItem {} }
  record Watchlist(List<WatchlistItem> items, String updatedAt) {}
  record MarketData(StocksFile stocks, CryptoFile crypto, BaselinesFile baselines, MetadataFile metadata) {}
  enum DataKey { STOCKS("stocks.json"), CRYPTO("crypto.json"), BASELINES("baselines.json"), METADATA("metadata.json"), SYMBOLS("symbols.json"); String key() }
  record DailyBar(String date, double close) {}
  ```
- Produces: `EtTime.etDate(String iso)` / `etDate(Instant)` → `"yyyy-MM-dd"` in `America/New_York`; `EtTime.formatEtTime(String iso)` → `"HH:MM ET"`; `Baselines.derive(List<DailyBar> barsAscending): Optional<StockBaseline>`; `Baselines.barsStartDate(Instant now): LocalDate` (earlier of Dec 20 of previous ET year and ET today − 50 days).

- [ ] **Step 1: Generate the Gradle wrapper with Spring Initializr** (this is how Spring projects usually start): `curl -s https://start.spring.io/starter.zip -d type=gradle-project-kotlin -d javaVersion=21 -d bootVersion=<latest 4.1.x> -d groupId=com.stockheatmap -d artifactId=backend -o /tmp/backend.zip && unzip /tmp/backend.zip -d backend`. Keep `gradlew`, `gradlew.bat`, `gradle/`; replace the rest with the multi-project build. Root: Java toolchain 21 for all modules, `org.springframework.boot` and `io.spring.dependency-management` declared `apply false`, every module imports the Spring Boot and `org.springframework.cloud:spring-cloud-dependencies:2025.1.x` BOMs, JUnit Platform. Module deps: `core` → `jackson-databind`, `jsoup`; `integrations` → `core`, `spring-web`, AWS SDK v2 BOM (`s3`, `dynamodb`, `ssm`, `url-connection-client`), `slf4j-api`; `jobs` → `integrations`, `spring-context`; `functions` → `jobs`, `spring-boot-starter`, `spring-cloud-function-adapter-aws`, `com.amazonaws:aws-lambda-java-events`; `local` → `jobs`, `spring-boot-starter-web`. Test deps: `spring-boot-starter-test` (gives JUnit 5, AssertJ, Mockito).
- [ ] **Step 2: Write failing tests.**

```java
class ContractTest {
  ObjectMapper mapper = JsonMapper.builder().build();
  @ParameterizedTest
  @CsvSource({"stocks.json,StocksFile", "crypto.json,CryptoFile", "baselines.json,BaselinesFile",
              "metadata.json,MetadataFile", "watchlist.json,Watchlist"})
  void roundTripsContractSamples(String file, String type) throws Exception {
    JsonNode original = mapper.readTree(Path.of("../../contract", file).toFile());
    Object parsed = mapper.treeToValue(original, Class.forName("com.stockheatmap.core.model." + type));
    assertThat((JsonNode) mapper.valueToTree(parsed)).isEqualTo(original);
  }
}
class EtTimeTest {
  @Test void convertsToEastern() {
    assertThat(EtTime.etDate("2026-01-02T04:30:00Z")).isEqualTo("2026-01-01");
    assertThat(EtTime.formatEtTime("2026-10-02T18:35:00Z")).isEqualTo("14:35 ET");
  }
}
class BaselinesTest {
  @Test void keepsLast30ClosesAndLastCloseDate() { /* 40 weekday bars, closes 100..139 → closes.size 30, last 139.0, lastCloseDate = last bar date */ }
  @Test void recordsLastCloseOfEachYear() {
    var b = Baselines.derive(bars("2025-12-29", "2025-12-30", "2025-12-31", "2026-01-02", "2026-01-05")).orElseThrow();
    assertThat(b.yearEndCloses()).containsExactly(entry("2025", 102.0), entry("2026", 104.0));
  }
  @Test void shortAndEmptyHistory() {
    assertThat(Baselines.derive(bars("2026-10-01", "2026-10-02")).orElseThrow().closes()).containsExactly(100.0, 101.0);
    assertThat(Baselines.derive(List.of())).isEmpty();
  }
  @Test void barsStartAtEarlierOfDec20AndFiftyDaysAgo() {
    assertThat(Baselines.barsStartDate(Instant.parse("2026-10-04T21:00:00Z"))).isEqualTo(LocalDate.parse("2025-12-20"));
    assertThat(Baselines.barsStartDate(Instant.parse("2026-01-05T22:00:00Z"))).isEqualTo(LocalDate.parse("2025-11-16"));
  }
}
```

- [ ] **Step 3: Run** `cd backend && ./gradlew :core:test` → FAIL (classes missing).
- [ ] **Step 4: Implement** the records, `EtTime`, `Baselines` (`yearEndCloses` as a `TreeMap`).
- [ ] **Step 5: Run** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `git add -A && git commit -m "feat(backend): Gradle multi-project, JSON model, contract samples, baselines"`

### Task 2: Snapshot merge, freshness rule, universes

**Files:**
- Create: `backend/core/src/main/java/com/stockheatmap/core/{Merge,MergeResult,Freshness,StaleDataException,Universe}.java`
- Test: `backend/core/src/test/java/com/stockheatmap/core/{MergeTest,UniverseTest}.java`

**Interfaces:**
- Produces: `record MergeResult<T>(Map<String, T> merged, double freshRatio)`; `Merge.snapshot(Map<String, T> previous /* nullable */, Map<String, T> fresh, Collection<String> universe): MergeResult<T>`; `Freshness.MIN_FRESH_RATIO = 0.9`; `Freshness.assertFresh(double ratio, String what)` throws `StaleDataException` (unchecked) when `< 0.9`; `Universe.stocks(MetadataFile /* nullable */, List<WatchlistItem>): List<String>` (sorted, distinct); `Universe.watchlistCoinIds(List<WatchlistItem>): List<String>`.

- [ ] **Step 1: Write failing tests:** previous value carried for a universe ticker missing from `fresh` (2 of 3 fresh → ratio 2/3); tickers outside the universe dropped; empty universe → ratio 0; `assertFresh(0.89, "stocks")` throws, `assertFresh(0.9, …)` does not; `Universe.stocks(meta[MSFT, BRK.B], [stock PLTR, stock MSFT, crypto bitcoin])` → `[BRK.B, MSFT, PLTR]`; `Universe.stocks(null, List.of())` → empty; `watchlistCoinIds([crypto solana])` → `[solana]`.
- [ ] **Step 2: Run** `./gradlew :core:test` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(core): snapshot merge, freshness rule, universes`

### Task 3: Watchlist validation

**Files:**
- Create: `backend/core/src/main/java/com/stockheatmap/core/{WatchlistValidator,ValidationResult}.java`
- Test: `backend/core/src/test/java/com/stockheatmap/core/WatchlistValidatorTest.java`

**Interfaces:**
- Produces: `sealed interface ValidationResult { record Valid(List<WatchlistItem> items); record Invalid(String error, List<WatchlistItem> invalid) }`; `WatchlistValidator.MAX_ITEMS = 100`; `WatchlistValidator.validate(JsonNode body /* nullable */, SymbolsFile symbols): ValidationResult`.
- Rules: body must be an object with array `items` of `{type:"stock", symbol}` / `{type:"crypto", id, symbol}` with non-blank strings, else `Invalid("Invalid request body", [])`. Normalize stock symbol trim+uppercase; crypto id trim+lowercase, symbol trim+uppercase. > 100 → `Invalid("Watchlist can hold at most 100 items", [])`. Unknown symbols/ids and later duplicates → `Invalid("Unknown or duplicate items", <normalized offenders>)`.

- [ ] **Step 1: Write failing tests.**

```java
SymbolsFile symbols = new SymbolsFile("", List.of("AAPL", "BRK.B"), List.of("solana"));
@Test void normalizesInput() {
  assertThat(validate("""
      {"items":[{"type":"stock","symbol":" aapl "},{"type":"crypto","id":"Solana","symbol":"sol"}]}"""))
    .isEqualTo(new Valid(List.of(new StockItem("AAPL"), new CryptoItem("solana", "SOL"))));
}
@Test void acceptsShareClassTickers() {
  assertThat(validate("""{"items":[{"type":"stock","symbol":"brk.b"}]}""")).isInstanceOf(Valid.class);
}
@Test void rejectsUnknownAndDuplicates() {
  assertThat(validate("""
      {"items":[{"type":"stock","symbol":"AAPL"},{"type":"stock","symbol":"ZZZZ"},{"type":"stock","symbol":"aapl"}]}"""))
    .isEqualTo(new Invalid("Unknown or duplicate items", List.of(new StockItem("ZZZZ"), new StockItem("AAPL"))));
}
@Test void capsAt100() { /* 101 AAPL items → Invalid("Watchlist can hold at most 100 items", List.of()) */ }
@ParameterizedTest
@ValueSource(strings = {"null", "{}", "{\"items\":\"x\"}", "{\"items\":[{\"type\":\"etf\",\"symbol\":\"SPY\"}]}"})
void rejectsMalformedBodies(String json) {
  assertThat(validate(json)).isEqualTo(new Invalid("Invalid request body", List.of()));
}
```

- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(core): watchlist validation`

### Task 4: S&P 500 scrape parsing and validation

**Files:**
- Create: `backend/core/src/main/java/com/stockheatmap/core/{Sp500Parser,InvalidScrapeException}.java`, `backend/core/src/test/resources/fixtures/wikipedia-sp500.html`
- Test: `backend/core/src/test/java/com/stockheatmap/core/Sp500ParserTest.java`

**Interfaces:**
- Produces: `Sp500Parser.GICS_SECTORS` (11: Communication Services, Consumer Discretionary, Consumer Staples, Energy, Financials, Health Care, Industrials, Information Technology, Materials, Real Estate, Utilities); `Sp500Parser.parse(String html): List<Sp500Entry>` (jsoup; table `#constituents`; columns located by header text `Symbol`, `Security`, `GICS Sector`; cells trimmed); `Sp500Parser.validate(List<Sp500Entry>)` throws `InvalidScrapeException` unless 495–510 entries covering all 11 sectors.

- [ ] **Step 1: Record fixture:** `curl -A "stock-heatmap/1.0 (personal project)" -o backend/core/src/test/resources/fixtures/wikipedia-sp500.html "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"`.
- [ ] **Step 2: Write failing tests:** parse fixture → size ≥ 495, contains `new Sp500Entry("AAPL", "Apple Inc.", "Information Technology")`, symbols contain `BRK.B`, `validate` passes; first 400 rows → throws; rows without `Utilities` → throws; `parse("<html></html>")` → empty list.
- [ ] **Step 3: Run** → FAIL. **Step 4: Implement.** **Step 5: Run** → PASS.
- [ ] **Step 6: Commit** `feat(core): S&P 500 table parsing`

### Task 5: Retrying HTTP and Alpaca client

**Files:**
- Create: `backend/integrations/src/main/java/com/stockheatmap/integrations/http/{RetryingHttp,HttpStatusException,Sleeper}.java`, `.../alpaca/{AlpacaClient,MarketClock}.java`, `.../secrets/{SecretName,SecretsProvider}.java`, fixtures `backend/integrations/src/test/resources/fixtures/alpaca-{clock,snapshots,bars-page1,bars-page2,assets}.json`
- Test: `backend/integrations/src/test/java/com/stockheatmap/integrations/{http/RetryingHttpTest,alpaca/AlpacaClientTest}.java`

**Interfaces:**
- Produces:
  - `interface Sleeper { void sleep(long ms); Sleeper REAL = … }`.
  - `class RetryingHttp(RestClient restClient, Sleeper sleeper)`: `<T> T getJson(String url, Map<String, String> headers, Class<T> type)`, `<T> T getJson(String url, Map<String, String> headers, ParameterizedTypeReference<T> type)`, `String getText(String url, Map<String, String> headers)`. Retries 429, 5xx and `ResourceAccessException`, sleeping 500/1000/2000; other 4xx → `HttpStatusException(int status)` immediately; after the 3rd retry throws `HttpStatusException` (or the last IO exception).
  - `enum SecretName { ALPACA_KEY_ID("/stock-heatmap/alpaca-key-id", "ALPACA_KEY_ID"), ALPACA_SECRET("/stock-heatmap/alpaca-secret", "ALPACA_SECRET"), FINNHUB_KEY("/stock-heatmap/finnhub-key", "FINNHUB_KEY"), COINGECKO_KEY("/stock-heatmap/coingecko-key", "COINGECKO_KEY"); String ssmName(); String envName(); }`; `interface SecretsProvider { String get(SecretName name); }`.
  - `record MarketClock(boolean isOpen)`; `class AlpacaClient(RetryingHttp http, SecretsProvider secrets)`: `MarketClock getClock()`; `Map<String, StockQuote> getSnapshots(List<String> symbols)`; `Map<String, List<DailyBar>> getDailyBars(List<String> symbols, LocalDate start, Instant end)`; `List<String> listActiveSymbols()`. Secrets read per call (the provider caches).
  - Endpoints and mapping: clock `https://paper-api.alpaca.markets/v2/clock` (`is_open`); snapshots `https://data.alpaca.markets/v2/stocks/snapshots?symbols=…&feed=delayed_sip` in batches of 100 — `price = latestTrade.p`, `prevClose = prevDailyBar.c`, `volume = dailyBar.v`, `tradingDay = etDate(dailyBar.t)`, `asOf = latestTrade.t`; null/incomplete snapshots skipped; a batch failing after retries is logged and omitted (no throw). Bars `https://data.alpaca.markets/v2/stocks/bars?symbols=…&timeframe=1Day&start=…&end=…&feed=sip&adjustment=split&limit=10000`, batches of 100, follow `next_page_token` via `page_token`, `date = etDate(t)`, `close = c`, ascending. Assets `https://paper-api.alpaca.markets/v2/assets?status=active&asset_class=us_equity`. Headers `APCA-API-KEY-ID`, `APCA-API-SECRET-KEY`.

- [ ] **Step 1: Record fixtures** with keys from `.env`, e.g. `curl -H "APCA-API-KEY-ID: $ALPACA_KEY_ID" -H "APCA-API-SECRET-KEY: $ALPACA_SECRET" "https://data.alpaca.markets/v2/stocks/snapshots?symbols=AAPL,BRK.B,MSFT&feed=delayed_sip"`; bars with `symbols=AAPL,MSFT&limit=50` to get a `next_page_token`, then page 2; trim assets to ~5 entries.
- [ ] **Step 2: Write failing tests** using `MockRestServiceServer.bindTo(RestClient.builder())` and a recording `Sleeper`:
  - 503, 503, 200 → returns body; sleeps `[500, 1000]`.
  - 404 → `HttpStatusException` (status 404) after one request.
  - 429 ×4 → throws after 4 requests.
  - snapshots fixture → `get("BRK.B")` has price, prevClose and a `yyyy-MM-dd` tradingDay; 250 symbols → 3 requests; a batch answering 500 ×4 → result lacks those symbols, no exception.
  - bars page 1 + page 2 merged ascending; second request URL contains `page_token=`.
  - clock → `isOpen` boolean; assets contain `AAPL`.
- [ ] **Step 3: Run** `./gradlew :integrations:test` → FAIL. **Step 4: Implement.** **Step 5: Run** → PASS.
- [ ] **Step 6: Commit** `feat(integrations): retrying HTTP and Alpaca client`

### Task 6: CoinGecko, Coinbase, Finnhub, Wikipedia clients and coin mapping

**Files:**
- Create: `backend/core/src/main/java/com/stockheatmap/core/{model/CoinGeckoMarket,CryptoMapping}.java`, `backend/integrations/src/main/java/com/stockheatmap/integrations/{coingecko/CoinGeckoClient,coinbase/CoinbaseClient,finnhub/FinnhubClient,wikipedia/WikipediaClient}.java`, fixtures `coingecko-markets.json` (bitcoin, ethereum, binancecoin), `coingecko-history.json`, `coingecko-list.json` (trimmed), `coinbase-products.json` (trimmed; one `trading_disabled: true`), `finnhub-profile2.json`, `finnhub-profile2-empty.json` (`{}`)
- Test: `CryptoMappingTest` (core), `CoinGeckoClientTest`, `CoinbaseClientTest`, `FinnhubClientTest` (integrations)

**Interfaces:**
- Produces:
  - `@JsonIgnoreProperties(ignoreUnknown = true) record CoinGeckoMarket(String id, String symbol, String name, double current_price, double market_cap, Double price_change_percentage_24h_in_currency, Double price_change_percentage_7d_in_currency, Double price_change_percentage_30d_in_currency, Sparkline sparkline_in_7d)` with nested `record Sparkline(List<Double> price)`.
  - `CryptoMapping.toCoinQuote(CoinGeckoMarket m, Set<String> usdProducts, String now): CoinQuote` — percents ÷ 100 (null stays null), symbol uppercased, `coinbaseProductId = "<SYMBOL>-USD"` if in `usdProducts` else null, `sparkline7d = m.sparkline_in_7d().price()`.
  - `CoinGeckoClient(RetryingHttp, SecretsProvider)`: `List<CoinGeckoMarket> topMarkets(int n)`, `List<CoinGeckoMarket> marketsByIds(List<String> ids)`, `OptionalDouble priceOn(String id, LocalDate date)`, `List<String> listCoinIds()`. Base `https://api.coingecko.com/api/v3`, header `x-cg-demo-api-key`; markets query `vs_currency=usd&order=market_cap_desc&sparkline=true&price_change_percentage=24h,7d,30d` + `per_page=<n>&page=1` or `ids=<csv>`; history `/coins/{id}/history?date=dd-MM-yyyy&localization=false` → `market_data.current_price.usd`.
  - `CoinbaseClient(RetryingHttp)`: `Set<String> listUsdProducts()` — `https://api.exchange.coinbase.com/products`, keep `quote_currency == "USD" && status == "online" && !trading_disabled`.
  - `FinnhubClient(RetryingHttp, SecretsProvider)`: `OptionalDouble sharesOutstanding(String symbol)` — `https://finnhub.io/api/v1/stock/profile2?symbol=…&token=…` → `shareOutstanding × 1e6`, empty when absent.
  - `WikipediaClient(RetryingHttp)`: `String fetchSp500Html()` with `User-Agent: stock-heatmap/1.0 (personal project)`.

- [ ] **Step 1: Record fixtures** with `curl` (CoinGecko demo key header; Finnhub `AAPL` and `ZZZZ`).
- [ ] **Step 2: Write failing tests:** bitcoin → `coinbaseProductId "BTC-USD"`, `change24h` = fixture pct / 100; binancecoin with `{BTC-USD}` → null product; null 30d stays null; `topMarkets(50)` URL contains `per_page=50`; `marketsByIds(List.of("solana", "pepe"))` contains `ids=solana,pepe`; `priceOn` → history value, empty for `{}`; Coinbase set contains `BTC-USD`, not the disabled product; Finnhub AAPL ≈ `shareOutstanding × 1e6`, ZZZZ → empty.
- [ ] **Step 3: Run** → FAIL. **Step 4: Implement.** **Step 5: Run** → PASS.
- [ ] **Step 6: Commit** `feat(integrations): CoinGecko, Coinbase, Finnhub, Wikipedia clients`

### Task 7: Storage and secrets adapters

**Files:**
- Create: `backend/integrations/src/main/java/com/stockheatmap/integrations/store/{DataStore,FsDataStore,MemoryDataStore,S3DataStore}.java`, `.../watchlist/{WatchlistRepo,FsWatchlistRepo,MemoryWatchlistRepo,DynamoWatchlistRepo}.java`, `.../secrets/{EnvSecretsProvider,SsmSecretsProvider}.java`
- Test: matching `*Test.java` (Mockito mocks of `S3Client`, `DynamoDbClient`, `SsmClient`; `@TempDir` for Fs)

**Interfaces:**
- Produces:
  - `interface DataStore { <T> Optional<T> get(DataKey key, Class<T> type); void put(DataKey key, Object value); }`. `FsDataStore(Path dir, ObjectMapper)`; `MemoryDataStore(ObjectMapper)` (stores JSON strings so tests exercise real serialization; `Optional<String> raw(DataKey)`); `S3DataStore(S3Client, String bucket, ObjectMapper)` — `NoSuchKeyException` → empty; put with `contentType("application/json")`.
  - `interface WatchlistRepo { Watchlist get(String userId); Watchlist put(String userId, List<WatchlistItem> items); List<WatchlistItem> allItems(); }`. Missing → `new Watchlist(List.of(), null)`; `put` sets `updatedAt = Instant.now().toString()`. `FsWatchlistRepo(Path file, ObjectMapper)`, `MemoryWatchlistRepo(ObjectMapper)`, `DynamoWatchlistRepo(DynamoDbClient, String table, ObjectMapper)` — attributes `userId` (S), `items` (S, JSON), `updatedAt` (S); `allItems` scans with projection `items`.
  - `EnvSecretsProvider(Environment)` reads `SecretName.envName()`; blank → `IllegalStateException("Missing secret: <ENV_NAME>")`. `SsmSecretsProvider(Supplier<SsmClient>)` — on first `get`, one `GetParameters` for all four `ssmName()`s with `withDecryption(true)`, cached; missing → `IllegalStateException("Missing secret: <ssm name>")`. The `SsmClient` is created on first use (SnapStart).

- [ ] **Step 1: Write failing tests:** Fs store round-trips a `StocksFile`, empty for absent key; S3 `NoSuchKeyException` → empty, put request has the bucket, key `stocks.json`, content type `application/json`; Dynamo missing user → empty `Watchlist`, `allItems` flattens two users; SSM request has `withDecryption(true)` and the four exact names and is made once across two `get`s; env provider throws `Missing secret: FINNHUB_KEY`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(integrations): storage, watchlist repo and secrets adapters`

### Task 8: Stock and crypto refresh jobs

**Files:**
- Create: `backend/jobs/src/main/java/com/stockheatmap/jobs/{RefreshSummary,RefreshStocksJob,RefreshCryptoJob}.java`
- Test: `RefreshStocksJobTest`, `RefreshCryptoJobTest` (Mockito clients, `MemoryDataStore`, `MemoryWatchlistRepo`, fixed `Clock`)

**Interfaces:**
- Produces: `record RefreshSummary(String file, int entries, double freshRatio)`; `RefreshStocksJob(DataStore, WatchlistRepo, AlpacaClient, Clock)` and `RefreshCryptoJob(DataStore, WatchlistRepo, CoinGeckoClient, CoinbaseClient, Clock)`, each `RefreshSummary run()`; SLF4J logging.
- Behavior:
  - stocks: universe = `Universe.stocks(metadata, watchlist.allItems())`; empty → `IllegalStateException("No stock universe: run refresh-metadata first")`. Clock + snapshots, `Merge.snapshot` with previous quotes, `assertFresh(ratio, "stocks")`, put `new StocksFile(now, new Market(isOpen), merged)`.
  - crypto: `topMarkets(50)` (failure propagates, nothing written); watchlist ids not in the top 50 → one `marketsByIds` (failure logged → empty); universe = top ∪ extras; map with `CryptoMapping.toCoinQuote` + `listUsdProducts()`; merge with previous coins; `assertFresh(ratio, "crypto")`; put.

- [ ] **Step 1: Write failing tests:** writes `stocks.json` with `market.isOpen` true; 8 of 10 fresh → `StaleDataException` and previous `stocks.json` unchanged; no metadata + empty watchlist → `IllegalStateException` with the exact message; watchlist coin already in top 50 → `marketsByIds` never called; extra coin `pepe` → `marketsByIds(["pepe"])` and `coins` contains it; `topMarkets` throwing → exception, no `crypto.json`.
- [ ] **Step 2: Run** `./gradlew :jobs:test` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(jobs): stock and crypto refresh`

### Task 9: Baselines and metadata jobs

**Files:**
- Create: `backend/jobs/src/main/java/com/stockheatmap/jobs/{RefreshBaselinesJob,RefreshMetadataJob}.java`
- Test: `RefreshBaselinesJobTest`, `RefreshMetadataJobTest`

**Interfaces:**
- Produces: `RefreshBaselinesJob(DataStore, WatchlistRepo, AlpacaClient, CoinGeckoClient, Clock, Sleeper)`; `RefreshMetadataJob(DataStore, WikipediaClient, FinnhubClient, AlpacaClient, CoinGeckoClient, Clock, Sleeper)`; both `RefreshSummary run()`.
- Behavior:
  - baselines: stock universe as Task 8; `getDailyBars(universe, Baselines.barsStartDate(now), now.minus(20, MINUTES))`; `Baselines.derive` per symbol; symbols without bars keep their previous baseline; `assertFresh(freshCount / universe.size(), "baselines")`. Coin ids = keys of stored `crypto.json` coins; ids whose previous `crypto[id].year` ≠ current ET year → `priceOn(id, Jan 1)` with `sleep(2500)` between calls; empty → omit. Put `baselines.json`.
  - metadata: `Sp500Parser.parse` + `validate` (throws → nothing written); `sharesOutstanding` per symbol with `sleep(1091)` between calls; empty or exception → previous value if any; put `metadata.json`. Then `listActiveSymbols()` + `listCoinIds()` → put `symbols.json`; if either throws, log and rethrow **after** `metadata.json` is written.

- [ ] **Step 1: Write failing tests:** at `2026-10-04T21:00:00Z` bars requested with start `2025-12-20` and end `2026-10-04T20:40:00Z`; ticker without bars keeps previous baseline; `priceOn` called for `solana` (no entry) but not `bitcoin` (year 2026); `<html></html>` → `InvalidScrapeException`, no `metadata.json`; Finnhub empty for one symbol keeps previous shares; sleeper recorded `1091`; `listCoinIds` throwing → exception but `metadata.json` present.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(jobs): baselines and metadata refresh`

### Task 10: API services and Spring wiring

**Files:**
- Create: `backend/jobs/src/main/java/com/stockheatmap/jobs/{ApiResult,MarketDataService,WatchlistService,JobsConfiguration}.java`
- Test: `MarketDataServiceTest`, `WatchlistServiceTest`, `JobsConfigurationTest`

**Interfaces:**
- Produces:
  - `record ApiResult(int status, Object body)`.
  - `MarketDataService(DataStore)`: `MarketData load()` (four files; missing → null fields).
  - `WatchlistService(WatchlistRepo, DataStore, ObjectMapper)`: `ApiResult handle(String method, String userId, String body /* nullable */)` — `GET` → 200 `Watchlist`; `PUT` unparseable → 400 `{"error":"Invalid request body","invalid":[]}`; no `symbols.json` → 503 `{"error":"Symbol list not available yet"}`; `Invalid` → 400 `{error, invalid}`; valid → 200 saved `Watchlist`; other → 405.
  - `@Configuration JobsConfiguration`: `@Bean`s for `RetryingHttp` (from an injected `RestClient.Builder`, falling back to `RestClient.builder()`), the five clients, four jobs, two services, `Clock.systemUTC()`, `Sleeper.REAL`, and an `ObjectMapper` (`JsonMapper.builder().build()`) if none exists. Expects `DataStore`, `WatchlistRepo`, `SecretsProvider` from the profile configuration (Tasks 11, 16).

- [ ] **Step 1: Write failing tests:** empty store → all-null `MarketData`; GET unknown user → empty items, null `updatedAt`; PUT valid → 200 and repo updated; PUT `"{bad"` → 400; PUT without symbols → 503; PUT unknown symbol → 400 with `invalid`; DELETE → 405. `JobsConfigurationTest`: `ApplicationContextRunner` with `JobsConfiguration` plus memory beans starts and contains `RefreshStocksJob` and `WatchlistService` (shows Spring wiring the object graph).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(jobs): API services and Spring wiring`

### Task 11: Local Spring Boot app — mock API, refresh and smoke commands

**Files:**
- Create: `backend/local/src/main/java/com/stockheatmap/local/{LocalApplication,LocalConfiguration,MockApiController,RefreshCommand,SmokeCommand}.java`, `backend/local/src/main/resources/application.properties`, root `package.json` (scripts only for now)
- Test: `backend/local/src/test/java/com/stockheatmap/local/MockApiControllerTest.java` (`@WebMvcTest` + `MockMvc`)

**Interfaces:**
- Produces:
  - `application.properties`: `server.port=8787`, `spring.profiles.active=local`, `spring.config.import=optional:file:.env[.properties]`, `heatmap.data-dir=.data`, `heatmap.config-file=`. Gradle `bootRun { workingDir = rootProject.projectDir.parentFile }` so paths resolve from the repo root.
  - `@Configuration @Profile("local") @Import(JobsConfiguration.class) LocalConfiguration`: `FsDataStore(heatmap.data-dir)`, `FsWatchlistRepo(<data-dir>/watchlist.json)`, `EnvSecretsProvider`.
  - `MockApiController`: `GET /config.json` → contents of `heatmap.config-file` if non-empty, else `{"apiUrl":"/api","auth":null}`; `GET /api/market-data` → `MarketDataService.load()`; `GET|PUT /api/watchlist` → `WatchlistService.handle(method, "local", body)` as `ResponseEntity`.
  - `RefreshCommand` (`ApplicationRunner`, runs when option `--refresh=<stocks|crypto|baselines|metadata|all>` is present; `all` order metadata, baselines, stocks, crypto; then exits). `SmokeCommand` (option `--smoke`): one real call per client (Alpaca clock, CoinGecko `topMarkets(1)`, Finnhub AAPL, Wikipedia parse + validate, Coinbase products), prints `OK <name>` / `FAIL <name>: <reason>`, exit code 1 if any fail.
  - Root npm scripts: `api:local` = `cd backend && ./gradlew :local:bootRun`; `refresh` = `cd backend && ./gradlew :local:bootRun --args="--spring.main.web-application-type=none --refresh=${JOB:-all}"`; `smoke` = same with `--smoke`.

- [ ] **Step 1: Write failing test:** `@WebMvcTest(MockApiController.class)` with `@MockitoBean MarketDataService` → `GET /api/market-data` 200 and `$.stocks.quotes.AAPL.price` present; `GET /config.json` → `{"apiUrl":"/api","auth":null}`; `@MockitoBean WatchlistService` returning `ApiResult(400, …)` → `PUT /api/watchlist` is HTTP 400.
- [ ] **Step 2: Run** `./gradlew :local:test` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Verify against real APIs (needs `.env`):** `npm run smoke` → five `OK` lines. `npm run refresh` (~10 min) → `.data/` holds all five files; `jq '.quotes.AAPL' .data/stocks.json` shows a price. `npm run api:local`, then `curl -s localhost:8787/api/market-data | jq '.metadata.sp500 | length'` → ~503.
- [ ] **Step 6: Commit** `feat(local): Spring Boot mock API, refresh and smoke commands`

---

## Phase 2 — Web app (spec §13 steps 2–3)

### Task 12: Web scaffold, shared TS types, contract check, change math, colors

**Files:**
- Create: root `tsconfig.base.json`, `eslint.config.js`; modify root `package.json` (workspaces `shared`, `web`, `infra`; scripts `typecheck`, `lint`, `test`, `dev` = `concurrently "npm run api:local" "npm run dev -w web"`); `shared/{package.json,tsconfig.json}`, `shared/src/{index,types,time}.ts`, `web/{package.json,tsconfig.json,vite.config.ts,index.html}`, `web/src/{main.tsx,config.ts,styles.css}`, `web/src/api/{client.ts,ApiProvider.tsx,useMarketData.ts}`, `web/src/heatmap/{change,color}.ts`
- Test: `shared/test/{contract,time}.test.ts`, `web/src/heatmap/{change,color}.test.ts`, `web/src/api/client.test.ts`

**Interfaces:**
- Produces (`@sh/shared`, mirrors the Task 1 records): `Timeframe` (`"1D" | "1W" | "1M" | "YTD"`), `TIMEFRAMES`, `StockQuote`, `StocksFile` (`market: { isOpen: boolean }`), `CoinQuote` (changes `number | null`, `coinbaseProductId: string | null`), `CryptoFile`, `StockBaseline`, `CoinBaseline`, `BaselinesFile`, `Sp500Entry`, `MetadataFile`, `WatchlistItem` (`{ type: "stock"; symbol } | { type: "crypto"; id; symbol }`), `Watchlist`, `MarketData` (nullable fields), `AppConfig { apiUrl: string; auth: { authority: string; clientId: string } | null }`; `etDate(iso)`, `formatEtTime(iso)`.
- Produces (web): `loadConfig(): Promise<AppConfig>` (`/config.json`, `cache: "no-store"`); `ApiError { status; body }`; `apiFetch<T>(config, path, token | null, init?)` (adds `Authorization: Bearer <token>`); `ApiProvider({ config, token, children })` + `useApi()`; `useMarketData(): { data: MarketData | undefined; isError: boolean }` (key `["market-data"]`, `refetchInterval: 60_000`, `placeholderData: keepPreviousData`, `retry: false`); `computeChange(price, baseline): number | null`; `stockBaselineFor(quote, b, tf)` — 1D → `prevClose`; `prior = tradingDay > lastCloseDate ? closes : closes.slice(0, -1)`; 1W `prior.at(-5)`, 1M `prior.at(-21)`, YTD `yearEndCloses[year(tradingDay) − 1]`; missing → null; `coinBaselineFor(coin, b, tf, year)` — 1D/1W/1M `price / (1 + change)`; YTD `priceJan1` if `b.year === year`; `COLORS`, `CLAMP`, `changeColor(change, tf)` (linear RGB interpolation, clamped). Vite proxies `/api` and `/config.json` to `http://localhost:8787`.

- [ ] **Step 1: Write failing tests.**

```ts
// shared/test/contract.test.ts — same files the Java ContractTest round-trips
import stocks from "../../contract/stocks.json";
import watchlist from "../../contract/watchlist.json";
it("contract samples satisfy the TS types", () => {
  const s: StocksFile = stocks;                 // compile-time check under `npm run typecheck`
  const w = watchlist as Watchlist;
  expect(s.market.isOpen).toBeTypeOf("boolean");
  expect(w.items.map((i) => i.type)).toEqual(["stock", "crypto"]);
});
// same pattern for crypto.json, baselines.json, metadata.json

// web/src/heatmap/change.test.ts
const b = { closes: Array.from({ length: 30 }, (_, i) => i + 1), lastCloseDate: "2026-10-01", yearEndCloses: { "2025": 50 } };
const quote = (tradingDay: string) => ({ price: 40, prevClose: 30, volume: 1, tradingDay, asOf: "" });
expect(stockBaselineFor(quote("2026-10-02"), b, "1W")).toBe(26);
expect(stockBaselineFor(quote("2026-10-02"), b, "1M")).toBe(10);
expect(stockBaselineFor(quote("2026-10-01"), b, "1W")).toBe(25);    // baseline already includes today's close
expect(stockBaselineFor(quote("2026-10-02"), b, "1D")).toBe(30);
expect(stockBaselineFor(quote("2026-01-02"), { ...b, lastCloseDate: "2025-12-31" }, "YTD")).toBe(50);
expect(stockBaselineFor(quote("2027-01-04"), b, "YTD")).toBeNull();
expect(stockBaselineFor(quote("2026-10-02"), { ...b, closes: [1, 2, 3] }, "1M")).toBeNull();
expect(coinBaselineFor({ ...coin, price: 110, change7d: 0.1 }, undefined, "1W", 2026)).toBeCloseTo(100);
expect(coinBaselineFor(coin, { priceJan1: 90, year: 2025 }, "YTD", 2026)).toBeNull();
expect(computeChange(110, 100)).toBeCloseTo(0.1);
expect(computeChange(110, null)).toBeNull();

// web/src/heatmap/color.test.ts
expect(changeColor(-0.03, "1D")).toBe("#e5484d");
expect(changeColor(0.05, "1D")).toBe("#30a46c");
expect(changeColor(0, "YTD")).toBe("#3b3f4a");
expect(changeColor(null, "1W")).toBe("#2a2d35");
expect(changeColor(0.05, "YTD")).not.toBe("#30a46c");
```

  Plus `etDate`/`formatEtTime` (same cases as Java `EtTimeTest`) and `apiFetch` (Bearer header; 400 → `ApiError` with body).
- [ ] **Step 2: Run** `npm test` → FAIL. **Step 3: Implement.** **Step 4: Run** `npm test && npm run typecheck && npm run lint` → PASS.
- [ ] **Step 5: Commit** `feat(web): scaffold, shared types, contract check, change math, colors`

### Task 13: Treemap layout and tile building

**Files:**
- Create: `web/src/heatmap/layoutTreemap.ts`, `web/src/heatmap/buildTiles.ts`
- Test: `web/src/heatmap/{layoutTreemap,buildTiles}.test.ts`

**Interfaces:**
- Produces: `TreemapItem { id; group: string | null; size }`; `Rect { x; y; w; h }`; `layoutTreemap(items, width, height, grouped): { tiles: (Rect & { id })[]; groups: (Rect & { name })[] }` — d3 `treemapSquarify`, `paddingInner(1)`, `paddingTop(16)` on group nodes when grouped; size ≤ 0 or non-finite → smallest positive size present (or 1). `View = "sp500" | "watchlist" | "crypto"`; `Tile { id; label; name; group; size; price; change; volume; delayed; spark }`; `buildTiles(view, data, live, watchlist, tf, year): Tile[]` — sp500: one per `metadata.sp500` entry with a quote, `size = price × shares` (missing → 0), `group = sector`, `spark = prior closes + price`; crypto: top 50 by `marketCap`, `price = live[coinbaseProductId] ?? coin.price`, `delayed = coinbaseProductId === null`, `spark = sparkline7d`; watchlist: in order, size 1, missing data → `price`/`change` null.

- [ ] **Step 1: Write failing tests:** tiles inside bounds, non-overlapping; equal sizes → areas within 1%; size `0` and `NaN` items get area > 0; grouped → one group rect per group, tiles inside their group; sp500 ticker without shares → tile with `size: 0`; crypto uses the live price and marks BNB `delayed`; 60 coins → 50 tiles; watchlist stock without quote → `change: null`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(web): treemap layout and tile building`

### Task 14: Heatmap UI, status bar, tile detail

**Files:**
- Create: `web/src/App.tsx`, `web/src/heatmap/Heatmap.tsx`, `web/src/detail/{TileDetail,Sparkline}.tsx`, `web/src/status/{marketStatus.ts,StatusBar.tsx}`
- Test: `web/src/status/marketStatus.test.ts`, `web/src/App.test.tsx`

**Interfaces:**
- Produces: `StockStatus = { kind: "none" } | { kind: "closed"; asOf } | { kind: "delayed"; since } | { kind: "ok"; asOf }`; `stockStatus(stocks, now)` (delayed when open and older than 25 min); `StatusBar` with the Global Constraints copy, plus `Retrying…` when `isError`; `Heatmap({ tiles, grouped, tf, onSelect })` — SVG `<g role="button" aria-label="<label> <change%>">` per tile, label + `±x.xx%` or `n/a` when `w > 40 && h > 24`, group names when grouped, `ResizeObserver` sizing; `TileDetail({ tile, onClose })` — name, price, % change, volume, `Sparkline`, `delayed` badge; `App` — view (default `sp500`), timeframe (default `1D`), tabs `S&P 500 / Watchlist / Crypto`, timeframe toggle, `Edit list` button (wired in Task 19), footer `Crypto data by CoinGecko`; all-null data → `Waiting for first data refresh`.

- [ ] **Step 1: Write failing tests:** `stockStatus` → `closed` / `delayed` (26 min) / `ok` (10 min) / `none`; App with mocked `/api/market-data` → clicking AAPL opens detail showing `Apple Inc.`; switching to `1W` changes the AAPL `fill`; all-null data → `Waiting for first data refresh`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** (`main.tsx`: `loadConfig()` → `QueryClientProvider` → `ApiProvider token={null}` → `App`). **Step 4: Run** → PASS.
- [ ] **Step 5: Visual check:** `npm run dev` (Spring mock API + Vite); agent-browser opens `http://localhost:5173`, screenshots the S&P view, clicks a tile, switches timeframes. Expected: sector-grouped map, recoloring, detail panel.
- [ ] **Step 6: Commit** `feat(web): heatmap UI, status bar, tile detail`

### Task 15: Live crypto stream

**Files:**
- Create: `web/src/crypto/{ticker,useCryptoStream}.ts`
- Modify: `web/src/App.tsx`, `web/src/status/StatusBar.tsx`
- Test: `web/src/crypto/{ticker,useCryptoStream}.test.ts`

**Interfaces:**
- Produces: `parseTicker(raw): { productId; price } | null` (only `type === "ticker"` with a finite price); `backoffDelay(attempt) = min(1000 × 2^attempt, 30000)`; `useCryptoStream(productIds, opts?: { WebSocketImpl?; flushMs? }): { prices; status: "connecting" | "live" | "reconnecting" }` — `wss://ws-feed.exchange.coinbase.com`, subscribe `{ type: "subscribe", product_ids, channels: ["ticker"] }`, flush buffered prices every `flushMs` (1000), reconnect with backoff (reset on open), resubscribe when ids change. While reconnecting `prices` keeps the last streamed values; coins never streamed use the cached CoinGecko price via `buildTiles`. StatusBar shows `Crypto live ●` / `Reconnecting…`.

- [ ] **Step 1: Write failing tests:** `parseTicker` → null for `subscriptions`, `error`, non-numeric price, invalid JSON; parses a real ticker; `backoffDelay(0) === 1000`, `backoffDelay(5) === 30000`; fake WebSocket + fake timers: two BTC ticks within 1 s → one update with the latest; close → `reconnecting`, new socket after 1000 ms.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Visual check** (agent-browser, Crypto tab): `Crypto live ●`, prices move within seconds, BNB shows `delayed`.
- [ ] **Step 6: Commit** `feat(web): live crypto prices from Coinbase`

---

## Phase 3 — AWS (spec §13 steps 4–9)

### Task 16: Spring Cloud Function app, CDK stack, stock/crypto Lambdas (first deploy)

**Files:**
- Create: `backend/functions/src/main/java/com/stockheatmap/functions/{FunctionsApplication,AwsConfiguration,LambdaFunctions}.java`, `backend/functions/src/main/resources/application.properties`, `backend/functions/src/test/java/com/stockheatmap/functions/LambdaFunctionsTest.java` (+ `src/test/resources/application-test.properties` and a `@TestConfiguration` with memory adapters), `infra/{package.json,tsconfig.json,cdk.json}`, `infra/bin/app.ts`, `infra/lib/{stack,lambda,data,jobs}.ts`, `infra/test/stack.test.ts`, `infra/test/fixtures/lambda.zip` (any tiny zip)
- Modify: `backend/functions/build.gradle.kts` (task `lambdaZip`), root `package.json` (`build:backend`)

**Interfaces:**
- Produces (backend):
  - `application.properties`: `spring.profiles.active=aws`, `spring.main.web-application-type=none`, `spring.main.banner-mode=off`.
  - `@Configuration @Profile("aws") @Import(JobsConfiguration.class) AwsConfiguration`: `S3DataStore(S3Client, env DATA_BUCKET)`, `DynamoWatchlistRepo(DynamoDbClient, env WATCHLIST_TABLE)`, `SsmSecretsProvider(SsmClient::create)`; AWS clients built with `UrlConnectionHttpClient`.
  - `LambdaFunctions`: `@Bean Function<Map<String, Object>, RefreshSummary> refreshStocks(RefreshStocksJob job)` and likewise `refreshCrypto`, `refreshBaselines`, `refreshMetadata` (input ignored → `job.run()`; exceptions propagate so Lambda records an error). API beans arrive in Tasks 18–19.
  - Gradle `lambdaZip` (`Zip`, `archiveFileName = "functions-lambda.zip"`): `from(tasks.compileJava)`, `from(tasks.processResources)`, `into("lib") { from(configurations.runtimeClasspath) }`; `bootJar` disabled. Root script `build:backend` = `cd backend && ./gradlew build :functions:lambdaZip`.
- Produces (infra):
  - `makeFunction(scope, id, { name, functionBean, timeout, environment, codePath, memoryMb = 1024 })` → `{ fn: lambda.Function, alias: lambda.Alias }` — `Runtime.JAVA_21`, `ARM_64`, handler per Global Constraints, `Code.fromAsset(codePath)`, env `MAIN_CLASS`, `spring_cloud_function_definition`, `JAVA_TOOL_OPTIONS=-XX:+TieredCompilation -XX:TieredStopAtLevel=1`, `snapStart: SnapStartConf.ON_PUBLISHED_VERSIONS`, `LoggingFormat.JSON`, own `LogGroup` (14 days), `retryAttempts: 0`, name `stock-heatmap-<name>`, alias `live` on `fn.currentVersion`.
  - `DataResources`: `dataBucket` (BLOCK_ALL, S3-managed encryption, `enforceSSL`, DESTROY + `autoDeleteObjects`), `table` (`Watchlist`, PK `userId` S, PROVISIONED 1/1, RETAIN).
  - `Jobs` (props `{ data, codePath }`): `functions.stocks` (`refreshStocks`, 60 s), `functions.crypto` (`refreshCrypto`, 60 s). Schedules (`aws-scheduler` L2, target = **alias**, timezone `America/New_York`, retry attempts 0): stocks `cron(30-55/5 9 ? * MON-FRI *)`, `cron(0/5 10-15 ? * MON-FRI *)`, `cron(0-20/5 16 ? * MON-FRI *)`; crypto `rate(15 minutes)`. Grants: stocks — put `stocks.json`; read `stocks.json`, `metadata.json`; table read; `ssm:GetParameters` on the two Alpaca parameters. Crypto — put/read `crypto.json`; table read; SSM coingecko.
  - `StockHeatmapStack` (`env.region = "us-east-1"`, prop `codePath` default `../backend/functions/build/distributions/functions-lambda.zip`; tests use `test/fixtures/lambda.zip`); output `DataBucketName`. `cdk.json` `"app": "npx tsx bin/app.ts"`.

- [ ] **Step 1: Write failing tests.** Java: `@SpringBootTest` with profile `test` (memory adapters) and `@MockitoBean` jobs → `FunctionCatalog.lookup("refreshStocks")` applied to `Map.of()` returns the mocked `RefreshSummary` (shows how Spring Cloud Function finds beans by name). CDK: buckets block public access; table 1/1; four schedules with `ScheduleExpressionTimezone: "America/New_York"` targeting an alias ARN; functions `java21`, `arm64`, `SnapStart: { ApplyOn: "PublishedVersions" }`, env `spring_cloud_function_definition`; stocks role has `s3:PutObject` only on `…/stocks.json` and `ssm:GetParameters` only on the two Alpaca parameters; log groups retain 14 days.
- [ ] **Step 2: Run** `./gradlew :functions:test` and `npm test -w infra` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS; `npm run build:backend` creates the zip; `cd infra && npx cdk synth` → exit 0.
- [ ] **Step 5: First deploy (human-assisted; explain each command):**
  ```bash
  npx cdk bootstrap aws://<ACCOUNT>/us-east-1
  for p in alpaca-key-id:ALPACA_KEY_ID alpaca-secret:ALPACA_SECRET finnhub-key:FINNHUB_KEY coingecko-key:COINGECKO_KEY; do
    aws ssm put-parameter --type SecureString --overwrite --name "/stock-heatmap/${p%%:*}" --value "$(grep "^${p##*:}=" .env | cut -d= -f2-)"; done
  npm run build:backend && (cd infra && npx cdk deploy StockHeatmapStack --outputs-file ../cdk-outputs.json)
  aws s3 cp .data/metadata.json s3://<DataBucketName>/metadata.json     # seed until Task 17
  aws lambda invoke --function-name stock-heatmap-refresh-crypto:live /dev/stdout
  aws lambda invoke --function-name stock-heatmap-refresh-stocks:live /dev/stdout
  aws s3 ls s3://<DataBucketName>/
  ```
  Expected: `crypto.json`, `stocks.json`, `metadata.json` listed; responses are `RefreshSummary` JSON. CloudWatch logs show `Restore Duration` rather than a long `Init Duration` (SnapStart working).
- [ ] **Step 6: Commit** `feat: Spring Cloud Function Lambdas for stocks and crypto, first deploy`

### Task 17: Baselines and metadata Lambdas

**Files:**
- Modify: `infra/lib/jobs.ts`, `infra/test/stack.test.ts` (the beans already exist from Task 16)

**Interfaces:**
- Produces: `functions.baselines` (`refreshBaselines`, 300 s, schedule `cron(0 17 ? * MON-FRI *)`), `functions.metadata` (`refreshMetadata`, 840 s, schedule `cron(0 6 ? * SUN *)`). Grants: baselines — put `baselines.json`; read `baselines.json`, `metadata.json`, `crypto.json`; table read; SSM alpaca ×2 + coingecko. Metadata — put `metadata.json`, `symbols.json`; read `metadata.json`; SSM all four; no table access.

- [ ] **Step 1: Add failing assertions:** six schedules; metadata `Timeout: 840`; metadata role has no `dynamodb:*`; baselines role reads `crypto.json`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and verify:** `npm run build:backend && (cd infra && npx cdk deploy StockHeatmapStack --outputs-file ../cdk-outputs.json)`; `aws lambda invoke --invocation-type Event --function-name stock-heatmap-refresh-metadata:live /dev/null`; after ~10 min `symbols.json` exists and `metadata.json` is newer; invoke `stock-heatmap-refresh-baselines:live` → `baselines.json` written.
- [ ] **Step 6: Commit** `feat(infra): baselines and metadata Lambdas`

### Task 18: Cognito, HTTP API, market-data function, web login

**Files:**
- Create: `backend/functions/src/main/java/com/stockheatmap/functions/ApiResponses.java`, `infra/lib/api.ts`, `infra/test/api.test.ts`, `web/src/auth/AuthGate.tsx`
- Modify: `LambdaFunctions.java`, `LambdaFunctionsTest.java`, `infra/lib/stack.ts`, `web/src/main.tsx`, root `package.json` (`dev:remote`)

**Interfaces:**
- Produces (backend): `ApiResponses.json(int status, Object body, String acceptEncoding, ObjectMapper): APIGatewayV2HTTPResponse` — headers `content-type: application/json`, `cache-control: private, max-age=30`; when `acceptEncoding` contains `gzip`, gzip + base64 body, `isBase64Encoded = true`, `content-encoding: gzip`. `@Bean Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> getMarketData(MarketDataService, ObjectMapper)` → 200 with `load()`.
- Produces (infra): `Api` (props `{ data, codePath, siteUrls: string[] }`): `UserPool` (`selfSignUpEnabled: false`, email sign-in, `mfa: OPTIONAL` TOTP only, `featurePlan: LITE`), domain prefix `stock-heatmap-<account>`, public client (no secret, auth-code grant, scopes `openid` + `email`, callback/logout = `siteUrls`, access/id 1 h, refresh 30 d); `HttpApi` with CORS `allowOrigins: siteUrls`, headers `authorization`, `content-type`, methods GET/PUT; `HttpJwtAuthorizer` (issuer `https://cognito-idp.<region>.amazonaws.com/<poolId>`, audience `[clientId]`); route `GET /market-data` → `get-market-data` **alias** (`getMarketData`, 10 s; reads only the four data keys). Outputs `ApiUrl`, `CreateUserCommand` (`aws cognito-idp admin-create-user --user-pool-id <id> --username <EMAIL> --user-attributes Name=email,Value=<EMAIL> Name=email_verified,Value=true`), `WebConfig` (`Stack.toJsonString` of `AppConfig`, `auth.authority` = issuer). In this task `siteUrls = ["http://localhost:5173"]`.
- Produces (web): `AuthGate({ config, children })` — `config.auth` null → children with token null; else `react-oidc-context` `AuthProvider` (`authority`, `client_id`, `redirect_uri: window.location.origin`, `scope: "openid email"`, `automaticSilentRenew: true`, `onSigninCallback` strips the query string); signed out or silent-renew error → `signinRedirect()`; wraps `ApiProvider` with `user.access_token`. Root `dev:remote` = `jq -r '.StockHeatmapStack.WebConfig' cdk-outputs.json > web/config.remote.json && concurrently "cd backend && ./gradlew :local:bootRun --args=--heatmap.config-file=web/config.remote.json" "npm run dev -w web"`.

- [ ] **Step 1: Write failing tests:** Java — `getMarketData` with `accept-encoding: gzip` → body gunzips to the mocked `MarketData` JSON; without → plain JSON. CDK — `AllowAdminCreateUserOnly: true`; client without secret, `CallbackURLs` contains `http://localhost:5173`; `GET /market-data` has `AuthorizationType: JWT`; get-market-data role has only `s3:GetObject*` on the four keys.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and log in:** build + deploy (as Task 17); run `CreateUserCommand` with your email; `curl -s -o /dev/null -w "%{http_code}" <ApiUrl>/market-data` → `401`; `npm run dev:remote`, sign in (new password, optional TOTP) → real data loads.
- [ ] **Step 6: Commit** `feat: Cognito login and authenticated market-data API`

### Task 19: Watchlist function, view and editor

**Files:**
- Create: `web/src/watchlist/{useWatchlist.ts,WatchlistEditor.tsx}`, `web/src/watchlist/WatchlistEditor.test.tsx`
- Modify: `LambdaFunctions.java`, `LambdaFunctionsTest.java`, `infra/lib/api.ts`, `infra/test/api.test.ts`, `web/src/App.tsx`

**Interfaces:**
- Produces (backend): `@Bean Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> watchlist(WatchlistService, ObjectMapper)` — `userId` = JWT claim `sub` from `requestContext.authorizer.jwt.claims`, method from `requestContext.http.method`, body base64-decoded when `isBase64Encoded`; response via `ApiResponses.json`.
- Produces (infra): routes `GET /watchlist`, `PUT /watchlist` (JWT) → `watchlist` alias (10 s; `grantReadWriteData` on the table; read `symbols.json` only).
- Produces (web): `useWatchlist(): { items; save(items): Promise<void>; invalid: WatchlistItem[]; error: string | null }` (key `["watchlist"]`; 400 `ApiError` sets `invalid`/`error` and keeps the old list); `WatchlistEditor({ coins, onClose })` — list with remove buttons; add row with type select `Stock`/`Crypto` + text input (crypto = CoinGecko id, `<datalist>` of top-50 `id — SYMBOL`); crypto symbol from `coins[id].symbol` else `id.toUpperCase()`; invalid rows `aria-invalid="true"`; `Save`. The Watchlist tab uses `buildTiles("watchlist", …)`.

- [ ] **Step 1: Write failing tests:** Java — PUT event with JWT `sub` `u1` calls `WatchlistService.handle("PUT", "u1", body)`; base64 body decoded. CDK — both routes JWT-protected; watchlist role has `dynamodb:PutItem` and reads only `symbols.json`. Web — add `zzzz`, Save with mocked 400 → `ZZZZ` row `aria-invalid="true"`, error shown, saved items still listed.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and verify** via `npm run dev:remote`: add `PLTR` and `solana`; reload in another browser profile → persisted; after the next stock refresh PLTR shows a price (1W/1M/YTD `n/a` until the next baselines run).
- [ ] **Step 6: Commit** `feat: watchlist API, view and editor`

### Task 20: CloudFront site hosting

**Files:**
- Create: `infra/lib/site.ts`, `infra/test/site.test.ts`, `infra/test/fixtures/web-dist/index.html`
- Modify: `infra/lib/stack.ts`, root `package.json` (`deploy` = `npm run build:backend && npm run build -w web && cd infra && npx cdk deploy StockHeatmapStack --outputs-file ../cdk-outputs.json`)

**Interfaces:**
- Produces: `Site` (props `{ webDistPath }`): private `siteBucket` (BLOCK_ALL, DESTROY + autoDelete); `Distribution` with `S3BucketOrigin.withOriginAccessControl`, `REDIRECT_TO_HTTPS`, `CACHING_OPTIMIZED`, `defaultRootObject: "index.html"`, 403/404 → `/index.html` 200 TTL 0; `site.url`; `site.deploy(config: AppConfig)` — `BucketDeployment`s (all `prune: false`): `assets/**` with `public, max-age=31536000, immutable`; `index.html` + `Source.jsonData("config.json", config)` with `no-cache` and invalidation `/index.html`, `/config.json`. Stack order `Site` → `Api({ siteUrls: [site.url, "http://localhost:5173"] })` → `site.deploy(webConfig)`. `webDistPath` default `../web/dist`; tests use the fixture. Output `SiteUrl`.

- [ ] **Step 1: Write failing tests:** `redirect-to-https`; 403/404 → `/index.html` 200; an `OriginAccessControl` exists; site bucket blocks public access; client `CallbackURLs` include a CloudFront domain and localhost.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and verify:** `npm run deploy`; open `SiteUrl` on laptop and phone; agent-browser runs the spec §9 end-to-end checklist.
- [ ] **Step 6: Commit** `feat(infra): CloudFront hosting with runtime config`

### Task 21: Monitoring, alarms and budget

**Files:**
- Create: `infra/lib/monitoring.ts`, `infra/test/monitoring.test.ts`
- Modify: `infra/lib/stack.ts`, `infra/bin/app.ts`

**Interfaces:**
- Produces: `Monitoring` (props `{ functions: { stocks, crypto, baselines, metadata }, alertEmail }`): SNS topic + `EmailSubscription`; `fn.metricErrors()` alarms (Sum, `>= 1`, `NOT_BREACHING`, action → topic): stocks 5 min × 3 of 3; crypto 15 min × 3 of 3; baselines and metadata 1 h × 1 of 1. `CfnBudget` monthly `COST` 1 USD, `FORECASTED` `GREATER_THAN` 100% → `alertEmail`. `bin/app.ts` throws `Error("ALERT_EMAIL is required")` when `process.env.ALERT_EMAIL` is unset.

- [ ] **Step 1: Write failing tests:** four alarms with those periods/evaluations; budget `Amount: 1, Unit: "USD"` with `FORECASTED`; SNS email subscription.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy:** `ALERT_EMAIL=<you> npm run deploy`; confirm the SNS email. Optional fire drill: break the Alpaca secret during market hours, wait for the alarm email, restore it.
- [ ] **Step 6: Commit** `feat(infra): failure alarms and $1 budget`

---

## Phase 4 — CI/CD (spec §13 step 10)

### Task 22: CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: workflow on `push` and `pull_request`, job id `ci` (`ubuntu-latest`): checkout → `actions/setup-java@v4` (temurin 21) → `gradle/actions/setup-gradle@v4` → `actions/setup-node@v4` (22, npm cache) → `npm ci` → `npm run build:backend` (Java tests + zip) → `npm run typecheck` → `npm run lint` → `npm test` → `npm run build -w web` → `cd infra && npx cdk synth` with `ALERT_EMAIL: ci@example.invalid`. No AWS credentials.

- [ ] **Step 1: Verify locally:** the same sequence from a clean `npm ci` → exit 0.
- [ ] **Step 2: Write** the workflow.
- [ ] **Step 3: Publish:** `gh repo create stock-heatmap --public --source . --push`; `gh run watch` → `ci` succeeds.
- [ ] **Step 4: Prove it catches failure:** on a branch break one JUnit assertion, push, confirm `ci` fails; revert and delete the branch.
- [ ] **Step 5: Commit** `ci: build, test and synth on every push`

### Task 23: OIDC deploy role, CD, post-deploy check, branch protection

**Files:**
- Create: `infra/lib/github-oidc-stack.ts`, `infra/test/github-oidc.test.ts`, `scripts/post-deploy-check.sh`
- Modify: `infra/bin/app.ts`, `.github/workflows/ci.yml`

**Interfaces:**
- Produces:
  - `GithubOidcStack` (props `{ repo }` from context `githubRepo`; only instantiated when set): `OpenIdConnectProvider` for `https://token.actions.githubusercontent.com` (client id `sts.amazonaws.com`); role `stock-heatmap-github-deploy` trusting it with `StringEquals` `…:aud = sts.amazonaws.com` and `…:sub = repo:<repo>:ref:refs/heads/main`; policy only `sts:AssumeRole` on `arn:aws:iam::<account>:role/cdk-hnb659fds-*-<account>-us-east-1`. Output `DeployRoleArn`.
  - `scripts/post-deploy-check.sh <outputs.json>`: `SiteUrl` body contains `<div id="root">`; `<ApiUrl>/market-data` without a token → `401`; otherwise non-zero exit with a message.
  - `ci.yml` job `deploy`: `needs: ci`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`, `permissions: { id-token: write, contents: read }`; Java/Gradle/Node setup as in `ci`; `aws-actions/configure-aws-credentials@v4` (`role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}`, `aws-region: us-east-1`); `npm ci` → `npm run deploy` with `ALERT_EMAIL: ${{ vars.ALERT_EMAIL }}` → `scripts/post-deploy-check.sh cdk-outputs.json`.

- [ ] **Step 1: Write failing assertion:** trust condition `sub` equals exactly `repo:owner/stock-heatmap:ref:refs/heads/main`; only action `sts:AssumeRole`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS; `scripts/post-deploy-check.sh cdk-outputs.json` against the live stack → exit 0.
- [ ] **Step 5: One-time setup (human):**
  ```bash
  (cd infra && npx cdk deploy StockHeatmapGithubOidc -c githubRepo=<owner>/stock-heatmap)
  gh variable set AWS_DEPLOY_ROLE_ARN --body <DeployRoleArn>
  gh variable set ALERT_EMAIL --body <you>
  gh api -X PUT repos/<owner>/stock-heatmap/branches/main/protection --input - <<'JSON'
  {"required_status_checks":{"strict":true,"contexts":["ci"]},"enforce_admins":false,"required_pull_request_reviews":null,"restrictions":null}
  JSON
  ```
- [ ] **Step 6: Ship through the pipeline:** commit on a branch, open a PR, merge after `ci` passes; `gh run watch` → `ci` then `deploy` succeed and the post-deploy check passes.
- [ ] **Step 7: Final end-to-end:** agent-browser run of the spec §9 checklist against `SiteUrl`; AWS Billing forecast shows $0.00.
