# Stock Heatmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a hosted, single-user stock/crypto treemap heatmap (S&P 500, Watchlist, Crypto) on AWS serverless at $0/month, deployed by GitHub Actions.

**Architecture:** Scheduled Lambdas fetch market data from free APIs and write JSON snapshots to a private S3 bucket; a Cognito-protected HTTP API serves the merged snapshot and the watchlist (DynamoDB) to a React SPA on CloudFront; the browser streams live crypto prices straight from Coinbase. All business logic is pure TypeScript shared by Lambdas and a local runner.

**Tech Stack:** Node.js 22, TypeScript (strict), npm workspaces, Vitest, React 19 + Vite, d3-hierarchy, TanStack Query 5, react-oidc-context 3 / oidc-client-ts 3, cheerio, AWS SDK v3, AWS CDK v2, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-04-stock-heatmap-design.md` — §14 overrides earlier sections.

## Global Constraints

- Node.js 22 everywhere; Lambda runtime `nodejs22.x`, `arm64`, not in a VPC.
- TypeScript `strict: true`; ESM source; workspaces `shared`, `functions`, `web`, `infra` named `@sh/shared`, `@sh/functions`, `@sh/web`, `@sh/infra`. `@sh/shared` is consumed as source (`"exports": "./src/index.ts"`).
- AWS region `us-east-1`. Stack names `StockHeatmapStack`, `StockHeatmapGithubOidc`.
- $0/month: no NAT, no Secrets Manager, no provisioned concurrency; DynamoDB provisioned 1 RCU / 1 WCU.
- All percentages stored and passed as **fractions** (2.5% → `0.025`). CoinGecko percents are divided by 100 at the client boundary.
- All stored timestamps ISO-8601 UTC; displayed in `America/New_York` as `HH:MM ET`.
- Data bucket keys exactly: `stocks.json`, `crypto.json`, `baselines.json`, `metadata.json`, `symbols.json`.
- SSM parameter names exactly: `/stock-heatmap/alpaca-key-id`, `/stock-heatmap/alpaca-secret`, `/stock-heatmap/finnhub-key`, `/stock-heatmap/coingecko-key`. Local env vars: `ALPACA_KEY_ID`, `ALPACA_SECRET`, `FINNHUB_KEY`, `COINGECKO_KEY`.
- Never commit secrets: `.env`, `.data/`, `cdk.out/`, `cdk-outputs.json`, `web/config.remote.json` are git-ignored.
- External calls retry 3× (delays 500, 1000, 2000 ms) on 429/5xx/network errors.
- `MIN_FRESH_RATIO = 0.9` — a refresh writes only if ≥ 90% of its universe is fresh.
- Exact UI copy: `Crypto data by CoinGecko`, `Stock data delayed since HH:MM ET`, `Market closed · as of HH:MM ET`, `Stocks as of HH:MM ET`, `Retrying…`, `Crypto live ●`, `Reconnecting…`, `delayed`, `n/a`, `Waiting for first data refresh`.
- Color scale: red `#e5484d` ← gray `#3b3f4a` → green `#30a46c`; clamps 1D ±0.03, 1W ±0.06, 1M ±0.10, YTD ±0.20; null change `#2a2d35`.
- GitHub repo public (spec §10); CI job id `ci`.

## Review Focus

1. **Share-class tickers (`BRK.B`, `BF.B`)** — must survive Wikipedia parsing, Alpaca snapshots and the watchlist unchanged; a mismatch silently drops a mega-cap tile. Tests: Task 4, Task 5, Task 3.
2. **Messy watchlist input (`" aapl "`, `"Solana"`)** — should be normalized (trim, uppercase stock, lowercase coin id), not rejected. Test: Task 3.
3. **Missing shares outstanding / market cap** (Finnhub returns `{}` for some tickers) — tile must still render (smallest size), not vanish. Tests: Task 13.
4. **Early January / short history** — YTD on the first sessions of a year uses the previous Dec 31 close; tickers with < 21 sessions get `n/a` for 1M, not a crash. Tests: Task 1, Task 12.
5. **First run / empty state** — no data files yet, empty watchlist, or Coinbase sending non-ticker messages: app shows `Waiting for first data refresh` / ignores the message instead of crashing. Tests: Task 14, Task 15.

## Prerequisites (human, before Task 5)

- Alpaca account (paper trading is enough) → API key id + secret.
- Finnhub free API key; CoinGecko Demo API key.
- AWS account, AWS CLI v2 configured for `us-east-1` with an admin-capable profile (before Task 16).
- GitHub account and `gh auth login` (before Task 22).
- Put the four keys in `stock-heatmap/.env` (copy `.env.example`).

## File Structure

```
package.json  tsconfig.base.json  eslint.config.js  .gitignore  .env.example
shared/src/        index.ts types.ts time.ts
functions/src/
  core/            baselines.ts merge.ts universe.ts watchlistValidation.ts sp500.ts cryptoMapping.ts
  clients/         http.ts alpaca.ts coingecko.ts coinbase.ts finnhub.ts wikipedia.ts
  adapters/        dataStore.ts s3DataStore.ts watchlistRepo.ts dynamoWatchlistRepo.ts secrets.ts
  jobs/            types.ts refreshStocks.ts refreshCrypto.ts refreshBaselines.ts refreshMetadata.ts
  api/             marketData.ts watchlistApi.ts
  handlers/        runtime.ts refreshStocks.ts refreshCrypto.ts refreshBaselines.ts refreshMetadata.ts getMarketData.ts watchlist.ts
  local/           refresh.ts smoke.ts mockApi.ts
functions/test/    (mirrors src) + fixtures/
web/src/
  main.tsx App.tsx config.ts styles.css
  api/             client.ts ApiProvider.tsx useMarketData.ts
  auth/            AuthGate.tsx
  heatmap/         change.ts color.ts layoutTreemap.ts buildTiles.ts Heatmap.tsx
  detail/          TileDetail.tsx Sparkline.tsx
  status/          marketStatus.ts StatusBar.tsx
  crypto/          ticker.ts useCryptoStream.ts
  watchlist/       useWatchlist.ts WatchlistEditor.tsx
infra/bin/app.ts
infra/lib/         stack.ts lambda.ts data.ts jobs.ts api.ts site.ts monitoring.ts github-oidc-stack.ts
infra/test/        *.test.ts fixtures/web-dist/index.html
scripts/post-deploy-check.sh
.github/workflows/ci.yml
```

---

## Phase 1 — Core logic and clients (spec §13 step 1)

### Task 1: Monorepo scaffold, shared types, stock baselines

**Files:**
- Create: `package.json`, `tsconfig.base.json`, `eslint.config.js`, `.gitignore`, `.env.example`, `shared/package.json`, `shared/tsconfig.json`, `shared/src/{index,types,time}.ts`, `functions/package.json`, `functions/tsconfig.json`, `functions/vitest.config.ts`, `functions/src/core/baselines.ts`
- Test: `shared/test/time.test.ts`, `functions/test/core/baselines.test.ts`

**Interfaces:**
- Produces (`@sh/shared`):
  ```ts
  export type Timeframe = "1D" | "1W" | "1M" | "YTD"; export const TIMEFRAMES: Timeframe[];
  export interface StockQuote { price: number; prevClose: number; volume: number; tradingDay: string; asOf: string }
  export interface StocksFile { asOf: string; market: { isOpen: boolean }; quotes: Record<string, StockQuote> }
  export interface CoinQuote { symbol: string; name: string; price: number; change24h: number | null; change7d: number | null; change30d: number | null; marketCap: number; sparkline7d: number[]; coinbaseProductId: string | null; asOf: string }
  export interface CryptoFile { asOf: string; coins: Record<string, CoinQuote> }
  export interface StockBaseline { closes: number[]; lastCloseDate: string; yearEndCloses: Record<string, number> }
  export interface CoinBaseline { priceJan1: number; year: number }
  export interface BaselinesFile { asOf: string; stocks: Record<string, StockBaseline>; crypto: Record<string, CoinBaseline> }
  export interface Sp500Entry { symbol: string; name: string; sector: string }
  export interface MetadataFile { asOf: string; sp500: Sp500Entry[]; sharesOutstanding: Record<string, number> }
  export interface SymbolsFile { asOf: string; validStockSymbols: string[]; validCoinIds: string[] }
  export type WatchlistItem = { type: "stock"; symbol: string } | { type: "crypto"; id: string; symbol: string };
  export interface Watchlist { items: WatchlistItem[]; updatedAt: string | null }
  export interface MarketData { stocks: StocksFile | null; crypto: CryptoFile | null; baselines: BaselinesFile | null; metadata: MetadataFile | null }
  export interface AppConfig { apiUrl: string; auth: { authority: string; clientId: string } | null }
  export const DATA_KEYS: { stocks: "stocks.json"; crypto: "crypto.json"; baselines: "baselines.json"; metadata: "metadata.json"; symbols: "symbols.json" };
  export type DataKey = (typeof DATA_KEYS)[keyof typeof DATA_KEYS];
  export function etDate(iso: string): string;        // "YYYY-MM-DD" in America/New_York
  export function formatEtTime(iso: string): string;  // "HH:MM ET", 24h
  ```
- Produces (`functions/src/core/baselines.ts`): `type DailyBar = { date: string; close: number }`; `deriveStockBaseline(bars: DailyBar[]): StockBaseline | null`; `barsStartDate(now: Date): string`.

- [ ] **Step 1: Scaffold the workspace.** Root `package.json` (`private`, `"type": "module"`, `workspaces`, scripts `typecheck` = `npm run typecheck --workspaces --if-present`, `lint` = `eslint .`, `test` = `npm test --workspaces --if-present`). `tsconfig.base.json`: `strict`, `target ES2022`, `module ESNext`, `moduleResolution Bundler`, `noEmit`, `skipLibCheck`. ESLint 9 flat config with `typescript-eslint` recommended. `.gitignore` per Global Constraints plus `node_modules/`, `dist/`. `.env.example` lists the four env vars empty. Each workspace gets `typecheck: tsc --noEmit -p .` and `test: vitest run`. Run `npm install` → exit 0.

- [ ] **Step 2: Write failing tests.**

```ts
// shared/test/time.test.ts
expect(etDate("2026-01-02T04:30:00Z")).toBe("2026-01-01");
expect(formatEtTime("2026-10-02T18:35:00Z")).toBe("14:35 ET");
```

```ts
// functions/test/core/baselines.test.ts
const bars = (dates: string[]) => dates.map((date, i) => ({ date, close: 100 + i }));
it("keeps the last 30 closes and lastCloseDate", () => {
  const b = deriveStockBaseline(bars(sessions("2026-08-03", 40)))!;   // helper: 40 weekday dates
  expect(b.closes).toHaveLength(30);
  expect(b.closes.at(-1)).toBe(139);
  expect(b.lastCloseDate).toBe(sessions("2026-08-03", 40).at(-1));
});
it("records the last close of each calendar year", () => {
  const b = deriveStockBaseline(bars(["2025-12-29", "2025-12-30", "2025-12-31", "2026-01-02", "2026-01-05"]))!;
  expect(b.yearEndCloses).toEqual({ "2025": 102, "2026": 104 });
});
it("handles short history and empty input", () => {
  expect(deriveStockBaseline(bars(["2026-10-01", "2026-10-02"]))!.closes).toEqual([100, 101]);
  expect(deriveStockBaseline([])).toBeNull();
});
it("starts bars at the earlier of Dec 20 last year and 50 days ago", () => {
  expect(barsStartDate(new Date("2026-10-04T21:00:00Z"))).toBe("2025-12-20");
  expect(barsStartDate(new Date("2026-01-05T22:00:00Z"))).toBe("2025-11-16");
});
```

- [ ] **Step 3: Run** `npm test` → FAIL (modules missing).
- [ ] **Step 4: Implement** `shared/src/*` and `functions/src/core/baselines.ts`. `etDate`/`formatEtTime` use `Intl.DateTimeFormat` with `timeZone: "America/New_York"` (`en-CA` gives `YYYY-MM-DD`; `hourCycle: "h23"`). `deriveStockBaseline` assumes bars ascending by date.
- [ ] **Step 5: Run** `npm test && npm run typecheck && npm run lint` → all pass.
- [ ] **Step 6: Commit** `git add -A && git commit -m "feat: scaffold monorepo, shared types, stock baselines"`

### Task 2: Snapshot merge, freshness rule, universes

**Files:**
- Create: `functions/src/core/merge.ts`, `functions/src/core/universe.ts`
- Test: `functions/test/core/merge.test.ts`, `functions/test/core/universe.test.ts`

**Interfaces:**
- Produces: `MIN_FRESH_RATIO = 0.9`; `class StaleDataError extends Error`; `mergeSnapshot<T>(prev: Record<string, T> | undefined, fresh: Record<string, T>, universe: string[]): { merged: Record<string, T>; freshRatio: number }`; `assertFresh(freshRatio: number, what: string): void` (throws `StaleDataError` when `< MIN_FRESH_RATIO`); `stockUniverse(metadata: MetadataFile | null, items: WatchlistItem[]): string[]` (sorted, unique); `watchlistCoinIds(items: WatchlistItem[]): string[]`.

- [ ] **Step 1: Write failing tests.**

```ts
const universe = ["AAPL", "MSFT", "NVDA"];
it("carries previous values for universe tickers missing from fresh", () => {
  const { merged, freshRatio } = mergeSnapshot({ NVDA: { v: 1, asOf: "old" } }, { AAPL: { v: 2, asOf: "new" }, MSFT: { v: 3, asOf: "new" } }, universe);
  expect(merged.NVDA).toEqual({ v: 1, asOf: "old" });
  expect(freshRatio).toBeCloseTo(2 / 3);
});
it("drops tickers outside the universe", () => {
  expect(mergeSnapshot({ OLD: { v: 1 } }, {}, universe).merged).not.toHaveProperty("OLD");
});
it("treats an empty universe as 0 fresh", () => expect(mergeSnapshot(undefined, {}, []).freshRatio).toBe(0));
it("assertFresh throws below 0.9", () => {
  expect(() => assertFresh(0.89, "stocks")).toThrow(StaleDataError);
  expect(() => assertFresh(0.9, "stocks")).not.toThrow();
});
it("stock universe = S&P ∪ watchlist stocks", () => {
  const meta = { asOf: "", sharesOutstanding: {}, sp500: [{ symbol: "MSFT", name: "", sector: "" }, { symbol: "BRK.B", name: "", sector: "" }] };
  expect(stockUniverse(meta, [{ type: "stock", symbol: "PLTR" }, { type: "stock", symbol: "MSFT" }, { type: "crypto", id: "bitcoin", symbol: "BTC" }]))
    .toEqual(["BRK.B", "MSFT", "PLTR"]);
  expect(stockUniverse(null, [])).toEqual([]);
  expect(watchlistCoinIds([{ type: "crypto", id: "solana", symbol: "SOL" }])).toEqual(["solana"]);
});
```

- [ ] **Step 2: Run** `npm test -w functions` → FAIL.
- [ ] **Step 3: Implement** both files.
- [ ] **Step 4: Run** `npm test -w functions` → PASS.
- [ ] **Step 5: Commit** `feat(core): snapshot merge, freshness rule, universes`

### Task 3: Watchlist validation

**Files:**
- Create: `functions/src/core/watchlistValidation.ts`
- Test: `functions/test/core/watchlistValidation.test.ts`

**Interfaces:**
- Produces: `MAX_WATCHLIST_ITEMS = 100`; `type WatchlistValidation = { ok: true; items: WatchlistItem[] } | { ok: false; error: string; invalid: WatchlistItem[] }`; `validateWatchlist(input: unknown, symbols: SymbolsFile): WatchlistValidation`.
- Rules: input must be `{ items: [...] }` with each item matching `WatchlistItem` shape, else `{ ok: false, error: "Invalid request body", invalid: [] }`. Normalize stock `symbol` → trimmed uppercase; crypto `id` → trimmed lowercase, crypto `symbol` → trimmed uppercase. > 100 items → `error: "Watchlist can hold at most 100 items"`. Unknown symbols/ids and later duplicates → `error: "Unknown or duplicate items"` with those items (normalized) in `invalid`.

- [ ] **Step 1: Write failing tests.**

```ts
const symbols = { asOf: "", validStockSymbols: ["AAPL", "BRK.B"], validCoinIds: ["solana"] };
it("normalizes input", () => {
  expect(validateWatchlist({ items: [{ type: "stock", symbol: " aapl " }, { type: "crypto", id: "Solana", symbol: "sol" }] }, symbols))
    .toEqual({ ok: true, items: [{ type: "stock", symbol: "AAPL" }, { type: "crypto", id: "solana", symbol: "SOL" }] });
});
it("accepts share-class tickers", () => expect(validateWatchlist({ items: [{ type: "stock", symbol: "brk.b" }] }, symbols).ok).toBe(true));
it("rejects unknown and duplicate items", () => {
  const r = validateWatchlist({ items: [{ type: "stock", symbol: "AAPL" }, { type: "stock", symbol: "ZZZZ" }, { type: "stock", symbol: "aapl" }] }, symbols);
  expect(r).toEqual({ ok: false, error: "Unknown or duplicate items", invalid: [{ type: "stock", symbol: "ZZZZ" }, { type: "stock", symbol: "AAPL" }] });
});
it("caps at 100", () => {
  const items = Array.from({ length: 101 }, () => ({ type: "stock", symbol: "AAPL" }));
  expect(validateWatchlist({ items }, symbols)).toMatchObject({ ok: false, error: "Watchlist can hold at most 100 items" });
});
it.each([null, {}, { items: "x" }, { items: [{ type: "etf", symbol: "SPY" }] }])("rejects malformed body %j", (body) =>
  expect(validateWatchlist(body, symbols)).toEqual({ ok: false, error: "Invalid request body", invalid: [] }));
```

- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(core): watchlist validation`

### Task 4: S&P 500 scrape parsing and validation

**Files:**
- Create: `functions/src/core/sp500.ts`, `functions/test/fixtures/wikipedia-sp500.html`
- Test: `functions/test/core/sp500.test.ts`

**Interfaces:**
- Produces: `GICS_SECTORS` (11 strings: Communication Services, Consumer Discretionary, Consumer Staples, Energy, Financials, Health Care, Industrials, Information Technology, Materials, Real Estate, Utilities); `class InvalidScrapeError extends Error`; `parseSp500Table(html: string): Sp500Entry[]`; `validateSp500(entries: Sp500Entry[]): void` (throws unless 495–510 entries covering all 11 sectors).

- [ ] **Step 1: Record the fixture:** `curl -A "stock-heatmap/1.0 (personal project)" -o functions/test/fixtures/wikipedia-sp500.html "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"`.
- [ ] **Step 2: Write failing tests.**

```ts
const html = readFileSync(fixture("wikipedia-sp500.html"), "utf8");
it("parses the constituents table", () => {
  const rows = parseSp500Table(html);
  expect(rows.length).toBeGreaterThanOrEqual(495);
  expect(rows).toContainEqual({ symbol: "AAPL", name: "Apple Inc.", sector: "Information Technology" });
  expect(rows.map((r) => r.symbol)).toContain("BRK.B");
  expect(() => validateSp500(rows)).not.toThrow();
});
it("rejects too few rows or missing sectors", () => {
  const rows = parseSp500Table(html);
  expect(() => validateSp500(rows.slice(0, 400))).toThrow(InvalidScrapeError);
  expect(() => validateSp500(rows.filter((r) => r.sector !== "Utilities"))).toThrow(InvalidScrapeError);
});
it("returns [] for a page without the table", () => expect(parseSp500Table("<html></html>")).toEqual([]));
```

- [ ] **Step 3: Run** → FAIL. **Step 4: Implement** with `cheerio`: table `#constituents`, columns by header text `Symbol`, `Security`, `GICS Sector`; trim cells. **Step 5: Run** → PASS.
- [ ] **Step 6: Commit** `feat(core): S&P 500 table parsing`

### Task 5: HTTP retry wrapper and Alpaca client

**Files:**
- Create: `functions/src/clients/http.ts`, `functions/src/clients/alpaca.ts`, fixtures `functions/test/fixtures/alpaca-{clock,snapshots,bars-page1,bars-page2,assets}.json`, `functions/test/fakeHttp.ts`
- Test: `functions/test/clients/http.test.ts`, `functions/test/clients/alpaca.test.ts`

**Interfaces:**
- Produces (`http.ts`): `type Logger = (msg: string, data?: Record<string, unknown>) => void`; `interface Http { getJson<T>(url: string, headers?: Record<string, string>): Promise<T>; getText(url: string, headers?: Record<string, string>): Promise<string> }`; `createHttp(opts?: { fetchFn?: typeof fetch; sleep?: (ms: number) => Promise<void> }): Http`; `class HttpError extends Error { status: number }`; `sleep(ms: number): Promise<void>`.
- Produces (`alpaca.ts`): `interface AlpacaClient { getClock(): Promise<{ isOpen: boolean }>; getSnapshots(symbols: string[]): Promise<Record<string, StockQuote>>; getDailyBars(symbols: string[], start: string, end: string): Promise<Record<string, DailyBar[]>>; listActiveSymbols(): Promise<string[]> }`; `createAlpaca(http: Http, creds: { keyId: string; secret: string }, log?: Logger): AlpacaClient`.
- Test helper (`fakeHttp.ts`): `fakeHttp(routes: Record<string, unknown | Error>): Http & { calls: string[] }` — matches by URL substring.
- Endpoints (headers `APCA-API-KEY-ID`, `APCA-API-SECRET-KEY`):
  - clock: `https://paper-api.alpaca.markets/v2/clock` → `is_open`
  - snapshots: `https://data.alpaca.markets/v2/stocks/snapshots?symbols=…&feed=delayed_sip`, batches of 100. Map `price = latestTrade.p`, `prevClose = prevDailyBar.c`, `volume = dailyBar.v`, `tradingDay = etDate(dailyBar.t)`, `asOf = latestTrade.t`. Skip symbols with a null snapshot or missing fields. A batch that still fails after retries is logged and its symbols omitted (no throw).
  - bars: `https://data.alpaca.markets/v2/stocks/bars?symbols=…&timeframe=1Day&start=…&end=…&feed=sip&adjustment=split&limit=10000`, batches of 100, follow `next_page_token` via `page_token`. `date = etDate(t)`, `close = c`.
  - assets: `https://paper-api.alpaca.markets/v2/assets?status=active&asset_class=us_equity` → `symbol[]`.

- [ ] **Step 1: Record fixtures** with real keys from `.env` (needs human prerequisites), e.g. `curl -H "APCA-API-KEY-ID: $ALPACA_KEY_ID" -H "APCA-API-SECRET-KEY: $ALPACA_SECRET" "https://data.alpaca.markets/v2/stocks/snapshots?symbols=AAPL,BRK.B,MSFT&feed=delayed_sip"`. Bars: request `symbols=AAPL,MSFT&limit=50` so a `next_page_token` appears, then page 2. Trim assets to ~5 entries.
- [ ] **Step 2: Write failing tests.**

```ts
// http.test.ts
it("retries 5xx then succeeds", async () => {
  const sleep = vi.fn(async () => {});
  const fetchFn = vi.fn().mockResolvedValueOnce(res(503)).mockResolvedValueOnce(res(503)).mockResolvedValueOnce(res(200, { ok: 1 }));
  await expect(createHttp({ fetchFn, sleep }).getJson("u")).resolves.toEqual({ ok: 1 });
  expect(sleep.mock.calls).toEqual([[500], [1000]]);
});
it("does not retry 404", async () => { /* fetchFn called once; rejects HttpError with status 404 */ });
it("gives up after 3 retries", async () => { /* 429 ×4 → rejects HttpError(429); fetchFn called 4 times */ });

// alpaca.test.ts
it("maps snapshots, keeping share-class keys", async () => {
  const q = await createAlpaca(fakeHttp({ "/v2/stocks/snapshots": snapshotsFixture }), creds).getSnapshots(["AAPL", "BRK.B", "MSFT"]);
  expect(q["BRK.B"]).toMatchObject({ price: expect.any(Number), prevClose: expect.any(Number), tradingDay: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) });
});
it("splits snapshots into batches of 100", async () => { /* 250 symbols → 3 calls */ });
it("omits a failed batch instead of throwing", async () => { /* route returns Error → resolves {} */ });
it("follows bar pagination", async () => { /* page1 + page2 merged; AAPL bars ascending; calls[1] contains page_token= */ });
it("reads clock and assets", async () => { /* isOpen boolean; listActiveSymbols contains "AAPL" */ });
```

- [ ] **Step 3: Run** → FAIL. **Step 4: Implement.** **Step 5: Run** → PASS.
- [ ] **Step 6: Commit** `feat(clients): retrying http and Alpaca client`

### Task 6: CoinGecko, Coinbase, Finnhub, Wikipedia clients and coin mapping

**Files:**
- Create: `functions/src/clients/{coingecko,coinbase,finnhub,wikipedia}.ts`, `functions/src/core/cryptoMapping.ts`, fixtures `coingecko-markets.json` (bitcoin, ethereum, binancecoin), `coingecko-history.json`, `coingecko-list.json` (trimmed), `coinbase-products.json` (trimmed, include BTC-USD, ETH-USD, one `trading_disabled: true`), `finnhub-profile2.json`, `finnhub-profile2-empty.json` (`{}`)
- Test: `functions/test/clients/{coingecko,coinbase,finnhub}.test.ts`, `functions/test/core/cryptoMapping.test.ts`

**Interfaces:**
- Produces:
  - `interface CoinGeckoMarket { id: string; symbol: string; name: string; current_price: number; market_cap: number; price_change_percentage_24h_in_currency: number | null; price_change_percentage_7d_in_currency: number | null; price_change_percentage_30d_in_currency: number | null; sparkline_in_7d: { price: number[] } }`
  - `interface CoinGeckoClient { getMarkets(q: { top: number } | { ids: string[] }): Promise<CoinGeckoMarket[]>; getPriceOn(id: string, ddmmyyyy: string): Promise<number | null>; listCoinIds(): Promise<string[]> }`; `createCoinGecko(http: Http, apiKey: string)`. Base `https://api.coingecko.com/api/v3`, header `x-cg-demo-api-key`. Markets query: `vs_currency=usd&order=market_cap_desc&sparkline=true&price_change_percentage=24h,7d,30d` plus `per_page=<top>&page=1` or `ids=<comma list>`. History: `/coins/{id}/history?date=DD-MM-YYYY&localization=false` → `market_data.current_price.usd ?? null`.
  - `listUsdProducts(http: Http): Promise<Set<string>>` — `https://api.exchange.coinbase.com/products`, keep `quote_currency === "USD" && status === "online" && !trading_disabled`.
  - `interface FinnhubClient { getSharesOutstanding(symbol: string): Promise<number | null> }`; `createFinnhub(http: Http, token: string)` — `https://finnhub.io/api/v1/stock/profile2?symbol=…&token=…`, returns `shareOutstanding * 1e6`, or `null` when absent.
  - `fetchSp500Html(http: Http): Promise<string>` — `getText` with header `User-Agent: stock-heatmap/1.0 (personal project)`.
  - `toCoinQuote(m: CoinGeckoMarket, usdProducts: Set<string>, now: string): CoinQuote` — percents ÷ 100 (null stays null), `symbol` uppercased, `coinbaseProductId = usdProducts.has(\`${SYMBOL}-USD\`) ? that : null`, `sparkline7d = m.sparkline_in_7d.price`, `asOf = now`.

- [ ] **Step 1: Record fixtures** with `curl` (CoinGecko with `x-cg-demo-api-key`, Finnhub `profile2?symbol=AAPL` and `symbol=ZZZZ` for the empty one).
- [ ] **Step 2: Write failing tests.**

```ts
it("maps a CoinGecko market to a CoinQuote", () => {
  const btc = toCoinQuote(markets.find((m) => m.id === "bitcoin")!, new Set(["BTC-USD"]), "2026-10-04T00:00:00Z");
  expect(btc.coinbaseProductId).toBe("BTC-USD");
  expect(btc.change24h).toBeCloseTo(markets[0].price_change_percentage_24h_in_currency! / 100);
  expect(toCoinQuote(markets.find((m) => m.id === "binancecoin")!, new Set(["BTC-USD"]), "x").coinbaseProductId).toBeNull();
});
it("keeps null percent changes null", () => { /* market with null 30d → change30d null */ });
it("builds markets URLs for top and ids", async () => { /* calls[0] contains per_page=50; calls[1] contains ids=solana,pepe */ });
it("reads the Jan 1 price or null", async () => { /* history fixture → number; {} → null */ });
it("filters Coinbase products to online USD pairs", async () => { /* has BTC-USD, not the disabled one */ });
it("converts Finnhub shares to absolute and handles {}", async () => { /* AAPL ≈ shareOutstanding*1e6; ZZZZ → null */ });
```

- [ ] **Step 3: Run** → FAIL. **Step 4: Implement.** **Step 5: Run** → PASS.
- [ ] **Step 6: Commit** `feat(clients): CoinGecko, Coinbase, Finnhub, Wikipedia`

### Task 7: Storage and secrets adapters

**Files:**
- Create: `functions/src/adapters/{dataStore,s3DataStore,watchlistRepo,dynamoWatchlistRepo,secrets}.ts`
- Test: `functions/test/adapters/*.test.ts` (use `aws-sdk-client-mock` for S3, DynamoDB DocumentClient, SSM)

**Interfaces:**
- Produces:
  - `interface DataStore { getJson<T>(key: DataKey): Promise<T | null>; putJson(key: DataKey, value: unknown): Promise<void> }`; `class FsDataStore implements DataStore { constructor(dir: string) }`; `class MemoryDataStore implements DataStore { files: Map<DataKey, unknown> }`; `class S3DataStore implements DataStore { constructor(client: S3Client, bucket: string) }` — missing object (`NoSuchKey`) → `null`; put with `ContentType: application/json`.
  - `interface WatchlistRepo { get(userId: string): Promise<Watchlist>; put(userId: string, items: WatchlistItem[]): Promise<Watchlist>; allItems(): Promise<WatchlistItem[]> }`; `FsWatchlistRepo(file: string)`, `MemoryWatchlistRepo`, `DynamoWatchlistRepo(doc: DynamoDBDocumentClient, table: string)` — item `{ userId, items, updatedAt }`; missing → `{ items: [], updatedAt: null }`; `allItems` scans with `ProjectionExpression: "#i"` and flattens. `put` sets `updatedAt = new Date().toISOString()`.
  - `type SecretName = "alpacaKeyId" | "alpacaSecret" | "finnhubKey" | "coingeckoKey"`; `type Secrets = Record<SecretName, string>`; `SSM_PARAM: Record<SecretName, string>` (names from Global Constraints); `ENV_VAR: Record<SecretName, string>`; `loadSecretsFromEnv(names: SecretName[], env?: NodeJS.ProcessEnv): Pick<Secrets, …>`; `loadSecretsFromSsm(client: SSMClient, names: SecretName[]): Promise<Pick<Secrets, …>>` — one `GetParameters` call, `WithDecryption: true`, cached in module scope; any missing name throws `Error("Missing secret: <name>")`.

- [ ] **Step 1: Write failing tests:** Fs store round-trip in `mkdtemp` dir and `getJson` of absent key → `null`; S3 `NoSuchKey` → `null` and `PutObjectCommand` received `{ Bucket, Key: "stocks.json", ContentType: "application/json" }`; Dynamo `get` of missing user → `{ items: [], updatedAt: null }`, `allItems` flattens two users' items; SSM request has `WithDecryption: true` and the four exact names; env loader throws `Missing secret: finnhubKey` when unset.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(adapters): data store, watchlist repo, secrets`

### Task 8: Stock and crypto refresh jobs

**Files:**
- Create: `functions/src/jobs/types.ts`, `functions/src/jobs/refreshStocks.ts`, `functions/src/jobs/refreshCrypto.ts`
- Test: `functions/test/jobs/refreshStocks.test.ts`, `functions/test/jobs/refreshCrypto.test.ts`

**Interfaces:**
- Consumes: Tasks 2, 5 (`Logger`), 6, 7.
- Produces: `interface JobDeps { store: DataStore; watchlist: WatchlistRepo; now: () => Date; sleep: (ms: number) => Promise<void>; log: Logger }`; `refreshStocks(deps: JobDeps, alpaca: AlpacaClient): Promise<StocksFile>`; `refreshCrypto(deps: JobDeps, cg: CoinGeckoClient, usdProducts: () => Promise<Set<string>>): Promise<CryptoFile>`.
- Behavior:
  - stocks: universe = `stockUniverse(metadata, watchlist.allItems())`; empty → throw `Error("No stock universe: run refresh-metadata first")`. Fetch clock + snapshots, `mergeSnapshot` with previous `quotes`, `assertFresh(ratio, "stocks")`, write `{ asOf: now, market: { isOpen }, quotes }`.
  - crypto: `getMarkets({ top: 50 })` (failure propagates → no write); watchlist coin ids not in the top 50 → one `getMarkets({ ids })` call (failure logged, treated as `[]`); universe = top ids ∪ extra ids; map with `toCoinQuote`, merge with previous `coins`, `assertFresh(ratio, "crypto")`, write.

- [ ] **Step 1: Write failing tests** (fake clients + `MemoryDataStore` + `MemoryWatchlistRepo`):

```ts
it("writes stocks.json with market status", async () => {
  store.files.set("metadata.json", meta(["AAPL", "MSFT"]));
  const out = await refreshStocks(deps, fakeAlpaca({ isOpen: true, quotes: { AAPL: q, MSFT: q } }));
  expect(store.files.get("stocks.json")).toEqual(out);
  expect(out.market.isOpen).toBe(true);
});
it("keeps the previous file when < 90% fresh", async () => {
  store.files.set("metadata.json", meta(tickers(10))); store.files.set("stocks.json", previous);
  await expect(refreshStocks(deps, fakeAlpaca({ isOpen: true, quotes: only(tickers(10).slice(0, 8)) }))).rejects.toThrow(StaleDataError);
  expect(store.files.get("stocks.json")).toBe(previous);
});
it("fails clearly with no metadata and empty watchlist", async () =>
  await expect(refreshStocks(deps, fakeAlpaca({}))).rejects.toThrow("No stock universe: run refresh-metadata first"));
it("makes one markets call when watchlist coins are already in the top 50", async () => { /* getMarkets called once */ });
it("fetches watchlist coins outside the top 50 by id", async () => { /* second call { ids: ["pepe"] }; coins has pepe */ });
it("does not write crypto.json when the top-50 call fails", async () => { /* rejects; store has no crypto.json */ });
```

- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(jobs): stock and crypto refresh`

### Task 9: Baselines and metadata jobs

**Files:**
- Create: `functions/src/jobs/refreshBaselines.ts`, `functions/src/jobs/refreshMetadata.ts`
- Test: `functions/test/jobs/refreshBaselines.test.ts`, `functions/test/jobs/refreshMetadata.test.ts`

**Interfaces:**
- Produces: `refreshBaselines(deps: JobDeps, alpaca: AlpacaClient, cg: CoinGeckoClient): Promise<BaselinesFile>`; `refreshMetadata(deps: JobDeps, c: { fetchSp500Html: () => Promise<string>; finnhub: FinnhubClient; alpaca: AlpacaClient; cg: CoinGeckoClient }): Promise<MetadataFile>`.
- Behavior:
  - baselines: stock universe as in Task 8; `getDailyBars(universe, barsStartDate(now), new Date(now − 20 min).toISOString())`; `deriveStockBaseline` per symbol; symbols with no bars keep their previous baseline; `assertFresh(freshCount / universe.length, "baselines")`. Crypto ids = keys of `crypto.json` `coins` (none → `{}` carried from previous). For each id whose previous `crypto[id].year !== year(etDate(now))`, `getPriceOn(id, "01-01-<year>")` with `sleep(2500)` between calls; `null` → omit.
  - metadata: parse + `validateSp500` (throws → nothing written). For each S&P symbol `getSharesOutstanding` with `sleep(1091)` between calls (55/min); `null` or error → previous value if any. Write `metadata.json`. Then `listActiveSymbols()` + `listCoinIds()` → write `symbols.json`; if either fails, log and rethrow **after** `metadata.json` is written.

- [ ] **Step 1: Write failing tests:** bars requested with start `"2025-12-20"` at now `2026-10-04T21:00:00Z` and end `2026-10-04T20:40:00.000Z`; ticker without bars keeps previous baseline; Jan 1 price fetched only for ids lacking the current year (fake `getPriceOn` called for `solana` but not `bitcoin` whose previous `year` is 2026); invalid scrape (`"<html></html>"`) rejects with `InvalidScrapeError` and no `metadata.json` written; Finnhub `null` for one symbol keeps the previous shares value; `sleep` called with `1091`; `listCoinIds` failure rejects but `metadata.json` exists.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(jobs): baselines and metadata refresh`

### Task 10: API request logic

**Files:**
- Create: `functions/src/api/marketData.ts`, `functions/src/api/watchlistApi.ts`
- Test: `functions/test/api/marketData.test.ts`, `functions/test/api/watchlistApi.test.ts`

**Interfaces:**
- Produces: `loadMarketData(store: DataStore): Promise<MarketData>` (four keys in parallel; missing → `null`); `interface ApiResult { status: number; body: unknown }`; `handleWatchlistRequest(req: { method: string; userId: string; body?: string }, deps: { repo: WatchlistRepo; store: DataStore }): Promise<ApiResult>`.
- Behavior: `GET` → 200 `Watchlist`. `PUT` → unparseable JSON → 400 `{ error: "Invalid request body", invalid: [] }`; `symbols.json` missing → 503 `{ error: "Symbol list not available yet" }`; validation failure → 400 `{ error, invalid }`; success → 200 saved `Watchlist`. Other methods → 405.

- [ ] **Step 1: Write failing tests** for: all-null `MarketData` on an empty store; GET for unknown user → `{ items: [], updatedAt: null }`; PUT valid → 200 and repo updated; PUT `"{bad"` → 400; PUT with no symbols file → 503; PUT unknown symbol → 400 with `invalid`; DELETE → 405.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(api): market data and watchlist request logic`

### Task 11: Local runner, smoke check, mock API

**Files:**
- Create: `functions/src/local/{refresh,smoke,mockApi}.ts`, `functions/test/local/mockApi.test.ts`
- Modify: root `package.json` (scripts `refresh`, `smoke`, `dev`, `dev:remote`; devDeps `tsx`, `concurrently`, `dotenv`)

**Interfaces:**
- Produces: `createMockApi(opts: { store: DataStore; repo: WatchlistRepo; config: AppConfig }): http.Server` with routes `GET /config.json` → `config`; `GET /api/market-data` → `loadMarketData`; `GET|PUT /api/watchlist` → `handleWatchlistRequest` with `userId: "local"`. Listens on `8787` when run as a script; when env `CONFIG_FILE` is set, serves that file as `/config.json`, otherwise `{ apiUrl: "/api", auth: null }`.
- Scripts: `npm run refresh -- <stocks|crypto|baselines|metadata|all>` runs the Task 8/9 jobs with `FsDataStore(".data")`, `FsWatchlistRepo(".data/watchlist.json")`, secrets from `.env` (`all` order: metadata, baselines, stocks, crypto). `npm run smoke` makes one real call per client (Alpaca clock, CoinGecko `getMarkets({ top: 1 })`, Finnhub AAPL, Wikipedia parse + validate, Coinbase products) and prints `OK`/`FAIL <reason>` per line; exit code 1 if any fail. `npm run dev` = `concurrently` mock API + `npm run dev -w web` (web added in Task 12).

- [ ] **Step 1: Write failing test:** start `createMockApi` on port 0 with a `MemoryDataStore` holding `stocks.json`; `fetch(/api/market-data)` → 200 and `.stocks` equals stored; `/config.json` → `{ apiUrl: "/api", auth: null }`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Verify against real APIs** (needs `.env`): `npm run smoke` → five `OK` lines. `npm run refresh -- all` (~10 min) → `.data/` contains all five files; `jq '.quotes.AAPL' .data/stocks.json` shows a price.
- [ ] **Step 6: Commit** `feat(local): refresh runner, smoke check, mock API`

---

## Phase 2 — Web app (spec §13 steps 2–3)

### Task 12: Web scaffold, config, API client, change math, colors

**Files:**
- Create: `web/` via Vite React-TS template trimmed to `web/{package.json,tsconfig.json,vite.config.ts,index.html}`, `web/src/{main.tsx,config.ts,styles.css}`, `web/src/api/{client.ts,ApiProvider.tsx,useMarketData.ts}`, `web/src/heatmap/{change,color}.ts`
- Test: `web/src/heatmap/change.test.ts`, `web/src/heatmap/color.test.ts`, `web/src/api/client.test.ts`

**Interfaces:**
- Produces:
  - `loadConfig(): Promise<AppConfig>` (fetch `/config.json`, `cache: "no-store"`).
  - `class ApiError extends Error { status: number; body: unknown }`; `apiFetch<T>(config: AppConfig, path: string, token: string | null, init?: RequestInit): Promise<T>` — adds `Authorization: Bearer <token>` when token non-null, JSON body/headers.
  - `ApiProvider({ config, token, children })`, `useApi(): <T>(path: string, init?: RequestInit) => Promise<T>`.
  - `useMarketData(): { data: MarketData | undefined; isError: boolean }` — TanStack Query key `["market-data"]`, `refetchInterval: 60_000`, `placeholderData: keepPreviousData`, `retry: false`.
  - `computeChange(price: number | null, baseline: number | null): number | null` (null if either null or baseline ≤ 0).
  - `stockBaselineFor(quote: StockQuote, b: StockBaseline | undefined, tf: Timeframe): number | null` — 1D → `quote.prevClose`; otherwise `prior = closes` if `tradingDay > lastCloseDate`, else `closes.slice(0, -1)`; 1W → `prior.at(-5)`, 1M → `prior.at(-21)`, YTD → `yearEndCloses[String(year(tradingDay) − 1)]`; missing → `null`.
  - `coinBaselineFor(coin: CoinQuote, b: CoinBaseline | undefined, tf: Timeframe, year: number): number | null` — 1D/1W/1M → `coin.price / (1 + change)` using `change24h`/`change7d`/`change30d` (null → null); YTD → `b.priceJan1` when `b.year === year`.
  - `COLORS = { red: "#e5484d", gray: "#3b3f4a", green: "#30a46c", none: "#2a2d35" }`; `CLAMP: Record<Timeframe, number> = { "1D": 0.03, "1W": 0.06, "1M": 0.10, YTD: 0.20 }`; `changeColor(change: number | null, tf: Timeframe): string` — linear RGB interpolation gray→red / gray→green, clamped; returns `#rrggbb`.
- Vite dev server proxies `/api` and `/config.json` to `http://localhost:8787`.

- [ ] **Step 1: Write failing tests.**

```ts
const b = { closes: Array.from({ length: 30 }, (_, i) => i + 1), lastCloseDate: "2026-10-01", yearEndCloses: { "2025": 50 } };
const quote = (tradingDay: string) => ({ price: 40, prevClose: 30, volume: 1, tradingDay, asOf: "" });
it("picks baselines relative to the trading day", () => {
  expect(stockBaselineFor(quote("2026-10-02"), b, "1W")).toBe(26);
  expect(stockBaselineFor(quote("2026-10-02"), b, "1M")).toBe(10);
  expect(stockBaselineFor(quote("2026-10-01"), b, "1W")).toBe(25);   // baseline already includes today's close
  expect(stockBaselineFor(quote("2026-10-02"), b, "1D")).toBe(30);
});
it("uses last year's close for YTD, including early January", () => {
  expect(stockBaselineFor(quote("2026-01-02"), { ...b, lastCloseDate: "2025-12-31" }, "YTD")).toBe(50);
  expect(stockBaselineFor(quote("2027-01-04"), b, "YTD")).toBeNull();
});
it("returns null for short history", () => expect(stockBaselineFor(quote("2026-10-02"), { ...b, closes: [1, 2, 3] }, "1M")).toBeNull());
it("derives coin baselines", () => {
  expect(coinBaselineFor({ ...coin, price: 110, change7d: 0.1 }, undefined, "1W", 2026)).toBeCloseTo(100);
  expect(coinBaselineFor(coin, { priceJan1: 90, year: 2025 }, "YTD", 2026)).toBeNull();
});
it("computes change", () => { expect(computeChange(110, 100)).toBeCloseTo(0.1); expect(computeChange(110, null)).toBeNull(); });
it("colors with clamps", () => {
  expect(changeColor(-0.03, "1D")).toBe("#e5484d");
  expect(changeColor(0.05, "1D")).toBe("#30a46c");
  expect(changeColor(0, "YTD")).toBe("#3b3f4a");
  expect(changeColor(null, "1W")).toBe("#2a2d35");
  expect(changeColor(0.05, "YTD")).not.toBe("#30a46c");
});
it("apiFetch sends the bearer token and raises ApiError", async () => { /* stub fetch; header Authorization: "Bearer t"; 400 → ApiError status 400 with body */ });
```

- [ ] **Step 2: Run** `npm test -w web` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(web): scaffold, config, API client, change math, colors`

### Task 13: Treemap layout and tile building

**Files:**
- Create: `web/src/heatmap/layoutTreemap.ts`, `web/src/heatmap/buildTiles.ts`
- Test: `web/src/heatmap/layoutTreemap.test.ts`, `web/src/heatmap/buildTiles.test.ts`

**Interfaces:**
- Produces:
  - `interface TreemapItem { id: string; group: string | null; size: number }`; `interface Rect { x: number; y: number; w: number; h: number }`; `layoutTreemap(items: TreemapItem[], width: number, height: number, grouped: boolean): { tiles: (Rect & { id: string })[]; groups: (Rect & { name: string })[] }` — `d3-hierarchy` `treemap().tile(treemapSquarify)`, `paddingInner(1)`, `paddingTop(16)` on group nodes when `grouped`. Items with size ≤ 0 or non-finite get the smallest positive size present (or 1 if none).
  - `type View = "sp500" | "watchlist" | "crypto"`; `interface Tile { id: string; label: string; name: string; group: string | null; size: number; price: number | null; change: number | null; volume: number | null; delayed: boolean; spark: number[] }`; `buildTiles(view: View, data: MarketData, live: Record<string, number>, watchlist: WatchlistItem[], tf: Timeframe, year: number): Tile[]`.
  - sp500: one tile per `metadata.sp500` entry with a quote; `size = price × sharesOutstanding` (missing → 0); `group = sector`; `spark = prior closes + price`.
  - crypto: top 50 coins by `marketCap`; `price = live[coinbaseProductId] ?? coin.price`; `delayed = coinbaseProductId === null`; `spark = sparkline7d`; `group = null`.
  - watchlist: tiles in watchlist order, `size = 1`, `group = null`; items without data → `price: null, change: null`.

- [ ] **Step 1: Write failing tests:** tiles stay inside `0..width × 0..height` and do not overlap; three equal sizes → areas within 1% of each other; an item with size `0` and one with `NaN` both get area > 0; grouped layout returns one group rect per distinct group and each tile lies inside its group rect; `buildTiles("sp500")` with a ticker lacking shares still yields a tile with `size: 0`; `buildTiles("crypto")` uses a live price over the cached one and marks BNB (`coinbaseProductId: null`) `delayed: true`; 60 coins → 50 tiles; watchlist stock with no quote → `change: null`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(web): treemap layout and tile building`

### Task 14: Heatmap UI, status bar, tile detail

**Files:**
- Create: `web/src/App.tsx`, `web/src/heatmap/Heatmap.tsx`, `web/src/detail/{TileDetail,Sparkline}.tsx`, `web/src/status/{marketStatus.ts,StatusBar.tsx}`
- Test: `web/src/status/marketStatus.test.ts`, `web/src/App.test.tsx` (Testing Library + jsdom)

**Interfaces:**
- Produces: `type StockStatus = { kind: "none" } | { kind: "closed"; asOf: string } | { kind: "delayed"; since: string } | { kind: "ok"; asOf: string }`; `stockStatus(stocks: StocksFile | null, now: Date): StockStatus` (delayed when market open and `now − asOf > 25 min`); `StatusBar` renders `Stocks as of HH:MM ET` / `Market closed · as of HH:MM ET` / yellow `Stock data delayed since HH:MM ET`, plus `Retrying…` when `useMarketData().isError`. `Heatmap({ tiles, grouped, tf, onSelect })` renders SVG `<g role="button" aria-label="<label> <change%>">` per tile, label + `±x.xx%` (or `n/a`) when `w > 40 && h > 24`, group names when grouped; sizes itself with a `ResizeObserver`. `TileDetail({ tile, onClose })` shows name, price, % change, volume, `Sparkline` (SVG polyline), `delayed` badge. `App` holds view (`sp500` default), timeframe (`1D` default) and selected tile; renders tabs `S&P 500 / Watchlist / Crypto`, timeframe toggle, `Edit list` button (wired in Task 19), footer `Crypto data by CoinGecko`; with all-null `MarketData` shows `Waiting for first data refresh`.

- [ ] **Step 1: Write failing tests:** `stockStatus` → `closed` when `market.isOpen` false; `delayed` at 26 min; `ok` at 10 min; `none` for `null`. App test (mock `fetch` for `/api/market-data` with a small fixture): renders tiles; clicking the `AAPL` tile opens the detail panel showing `Apple Inc.`; switching to `1W` changes the AAPL tile `fill`; all-null data shows `Waiting for first data refresh`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** (`main.tsx`: `loadConfig()` → `QueryClientProvider` → `ApiProvider token={null}` → `App`; auth arrives in Task 18). **Step 4: Run** → PASS.
- [ ] **Step 5: Visual check:** `npm run dev`, then with agent-browser open `http://localhost:5173`, screenshot the S&P view, click a tile, switch timeframes. Expected: sector-grouped map, colors change per timeframe, detail panel opens.
- [ ] **Step 6: Commit** `feat(web): heatmap UI, status bar, tile detail`

### Task 15: Live crypto stream

**Files:**
- Create: `web/src/crypto/ticker.ts`, `web/src/crypto/useCryptoStream.ts`
- Modify: `web/src/App.tsx` (pass live prices to `buildTiles`), `web/src/status/StatusBar.tsx` (crypto status)
- Test: `web/src/crypto/ticker.test.ts`, `web/src/crypto/useCryptoStream.test.ts`

**Interfaces:**
- Produces: `parseTicker(raw: string): { productId: string; price: number } | null` (only `type === "ticker"` with finite numeric `price`); `backoffDelay(attempt: number): number` = `min(1000 × 2^attempt, 30000)`; `useCryptoStream(productIds: string[], opts?: { WebSocketImpl?: typeof WebSocket; flushMs?: number }): { prices: Record<string, number>; status: "connecting" | "live" | "reconnecting" }` — URL `wss://ws-feed.exchange.coinbase.com`, on open send `{ type: "subscribe", product_ids, channels: ["ticker"] }`, buffer updates and flush every `flushMs` (default 1000), reconnect on close with `backoffDelay(attempt)` (attempt resets on open), resubscribe when `productIds` changes.
- StatusBar shows `Crypto live ●` when `live`, `Reconnecting…` when `reconnecting`.
- While reconnecting, `prices` keeps the last streamed values (fresher than the cached CoinGecko price); coins with no streamed price use the cached CoinGecko price via `buildTiles`.

- [ ] **Step 1: Write failing tests:** `parseTicker` returns null for `{"type":"subscriptions"}`, `{"type":"error"}`, non-numeric price, invalid JSON; parses a real ticker message. `backoffDelay(0) === 1000`, `backoffDelay(5) === 30000`. Hook with a fake WebSocket + fake timers: two ticks for BTC-USD within 1 s cause one state update with the latest price; socket close → `reconnecting`, new socket after 1000 ms.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Visual check** with agent-browser on the Crypto tab: status shows `Crypto live ●`; tile prices change within a few seconds; BNB shows `delayed`.
- [ ] **Step 6: Commit** `feat(web): live crypto prices from Coinbase`

---

## Phase 3 — AWS (spec §13 steps 4–9)

### Task 16: CDK app, data resources, stock/crypto Lambdas (first deploy)

**Files:**
- Create: `infra/{package.json,tsconfig.json,cdk.json}`, `infra/bin/app.ts`, `infra/lib/{stack,lambda,data,jobs}.ts`, `functions/src/handlers/{runtime,refreshStocks,refreshCrypto}.ts`
- Test: `infra/test/stack.test.ts`

**Interfaces:**
- Produces:
  - `makeFunction(scope, id, props: { name: string; entry: string; timeout: Duration; memoryMb?: number; environment?: Record<string, string> }): NodejsFunction` — `Runtime.NODEJS_22_X`, `Architecture.ARM_64`, `LoggingFormat.JSON`, own `LogGroup` with `RetentionDays.TWO_WEEKS`, `retryAttempts: 0`, `functionName: "stock-heatmap-" + name`.
  - `DataResources` construct: `dataBucket` (BLOCK_ALL, S3-managed encryption, `enforceSSL`, `RemovalPolicy.DESTROY` + `autoDeleteObjects`), `table` (`Watchlist`, PK `userId` string, `BillingMode.PROVISIONED` 1/1, `RemovalPolicy.RETAIN`).
  - `Jobs` construct: `functions: { stocks, crypto, baselines?, metadata? }`. Env `DATA_BUCKET`, `WATCHLIST_TABLE`. Schedules (`aws-scheduler` L2, timezone `America/New_York`, target retry attempts 0): stocks `cron(30-55/5 9 ? * MON-FRI *)`, `cron(0/5 10-15 ? * MON-FRI *)`, `cron(0-20/5 16 ? * MON-FRI *)`; crypto `rate(15 minutes)`. Timeouts: stocks/crypto 60 s, 512 MB.
  - Grants (§5.4/§14): stocks — `grantPut` `stocks.json`, `grantRead` `stocks.json` + `metadata.json`, table read; crypto — `grantPut`/`grantRead` `crypto.json`, table read; `ssm:GetParameters` on only the needed parameter ARNs (stocks: alpaca ×2; crypto: coingecko).
  - `handlers/runtime.ts`: `createRuntime(): { deps: JobDeps; http: Http; secrets(names: SecretName[]): Promise<…> }` built from `process.env.DATA_BUCKET`/`WATCHLIST_TABLE`; `log` writes JSON via `console.log`. Handlers are glue: build clients from secrets, call the job, let errors throw.
  - `StockHeatmapStack` with `env: { region: "us-east-1" }`; outputs `DataBucketName`.

- [ ] **Step 1: Write failing CDK assertions:** both buckets block all public access; table has `ProvisionedThroughput { ReadCapacityUnits: 1, WriteCapacityUnits: 1 }`; four `AWS::Scheduler::Schedule` resources with `ScheduleExpressionTimezone: "America/New_York"`; stocks function role policy has `s3:PutObject` only on `…/stocks.json` and `ssm:GetParameters` only on the two Alpaca parameters; all functions `nodejs22.x` + `arm64`; log groups retain 14 days.
- [ ] **Step 2: Run** `npm test -w infra` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS; `cdk.json` has `"app": "npx tsx bin/app.ts"`; `npx cdk synth` (from `infra/`) → exit 0.
- [ ] **Step 5: First deploy (human-assisted, explain each command as it runs):**
  ```bash
  npx cdk bootstrap aws://<ACCOUNT>/us-east-1
  for p in alpaca-key-id:ALPACA_KEY_ID alpaca-secret:ALPACA_SECRET finnhub-key:FINNHUB_KEY coingecko-key:COINGECKO_KEY; do
    aws ssm put-parameter --type SecureString --overwrite --name "/stock-heatmap/${p%%:*}" --value "$(grep "^${p##*:}=" .env | cut -d= -f2-)"; done
  (cd infra && npx cdk deploy StockHeatmapStack)
  aws s3 cp .data/metadata.json s3://<DataBucketName>/metadata.json     # seed until Task 17
  aws lambda invoke --function-name stock-heatmap-refresh-crypto /dev/stdout
  aws lambda invoke --function-name stock-heatmap-refresh-stocks /dev/stdout
  aws s3 ls s3://<DataBucketName>/
  ```
  Expected: `crypto.json`, `stocks.json`, `metadata.json` listed; invokes return no `FunctionError`.
- [ ] **Step 6: Commit** `feat(infra): CDK stack, data resources, stock and crypto Lambdas`

### Task 17: Baselines and metadata Lambdas

**Files:**
- Create: `functions/src/handlers/{refreshBaselines,refreshMetadata}.ts`
- Modify: `infra/lib/jobs.ts`, `infra/test/stack.test.ts`

**Interfaces:**
- Produces: `functions.baselines` (timeout 300 s, 512 MB, schedule `cron(0 17 ? * MON-FRI *)`), `functions.metadata` (timeout 14 min, 256 MB, schedule `cron(0 6 ? * SUN *)`). Grants: baselines — put `baselines.json`; read `baselines.json`, `metadata.json`, `crypto.json`; table read; SSM alpaca ×2 + coingecko. Metadata — put `metadata.json`, `symbols.json`; read `metadata.json`; SSM finnhub + alpaca ×2 + coingecko; no table access.

- [ ] **Step 1: Add failing assertions:** six schedules total; metadata `Timeout: 840`; metadata role has no `dynamodb:*`; baselines role can `s3:GetObject` `crypto.json`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and verify:** `cdk deploy`; `aws lambda invoke --invocation-type Event --function-name stock-heatmap-refresh-metadata /dev/null`; after ~10 min `aws s3 ls` shows `symbols.json` and a new `metadata.json` timestamp; then invoke `stock-heatmap-refresh-baselines` synchronously → `baselines.json` written.
- [ ] **Step 6: Commit** `feat(infra): baselines and metadata Lambdas`

### Task 18: Cognito, HTTP API, market-data endpoint, web login

**Files:**
- Create: `infra/lib/api.ts`, `functions/src/handlers/getMarketData.ts`, `web/src/auth/AuthGate.tsx`
- Modify: `infra/lib/stack.ts`, `web/src/main.tsx`, root `package.json` (`dev:remote`)
- Test: `infra/test/api.test.ts`, `functions/test/handlers/getMarketData.test.ts`

**Interfaces:**
- Consumes: `DataResources`, `loadMarketData`.
- Produces:
  - `Api` construct (props `{ dataBucket, table, siteUrls: string[] }`): `UserPool` (`selfSignUpEnabled: false`, sign-in alias email, `mfa: OPTIONAL` with TOTP only, `featurePlan: LITE`), domain prefix `stock-heatmap-<account>`, public client (no secret, auth-code grant, scopes `openid` + `email`, callback/logout URLs = `siteUrls`, access/id token 1 h, refresh 30 d). `HttpApi` with CORS `allowOrigins: siteUrls`, headers `authorization`, `content-type`, methods GET/PUT; `HttpJwtAuthorizer` (issuer `https://cognito-idp.<region>.amazonaws.com/<poolId>`, audience `[clientId]`); route `GET /market-data` → `stock-heatmap-get-market-data` (10 s, 256 MB, `grantRead` on the four keys only). Outputs `ApiUrl`, `CreateUserCommand` (`aws cognito-idp admin-create-user --user-pool-id <id> --username <EMAIL> --user-attributes Name=email,Value=<EMAIL> Name=email_verified,Value=true`), `WebConfig` (`Stack.toJsonString` of `AppConfig` with `auth.authority` = issuer and `auth.clientId`).
  - In this task `siteUrls = ["http://localhost:5173"]`; Task 20 appends the CloudFront URL.
  - `getMarketData` handler: `loadMarketData(S3DataStore)`; when `accept-encoding` contains `gzip` return gzip base64 body with `isBase64Encoded: true`, `content-encoding: gzip`; always `content-type: application/json`, `cache-control: private, max-age=30`.
  - `AuthGate({ config, children })`: if `config.auth` is null render children with token `null`; else `react-oidc-context` `AuthProvider` (`authority`, `client_id`, `redirect_uri: window.location.origin`, `scope: "openid email"`, `automaticSilentRenew: true`, `onSigninCallback` strips the query string); signed out → `signinRedirect()`; renders `ApiProvider` with `user.access_token`. On silent renew failure → `signinRedirect()`.
  - Every deploy from here on uses `npx cdk deploy StockHeatmapStack --outputs-file ../cdk-outputs.json` (from `infra/`). `npm run dev:remote` = `jq -r '.StockHeatmapStack.WebConfig' cdk-outputs.json > web/config.remote.json && CONFIG_FILE=web/config.remote.json npm run dev` (no deploy).

- [ ] **Step 1: Write failing tests:** user pool `AdminCreateUserConfig.AllowAdminCreateUserOnly: true`; client `GenerateSecret` absent/false and `CallbackURLs` contains `http://localhost:5173`; route `GET /market-data` has `AuthorizationType: JWT`; get-market-data role has only `s3:GetObject*`-family actions on the four keys. Handler: gzip response round-trips via `gunzipSync` to the stored JSON; no `accept-encoding` → plain JSON body.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and log in:** `cdk deploy`; run the `CreateUserCommand` output with your email (temporary password arrives by email); `curl -s -o /dev/null -w "%{http_code}" <ApiUrl>/market-data` → `401`; `npm run dev:remote`, sign in at the Cognito page (set new password, optionally TOTP), heatmap loads real data.
- [ ] **Step 6: Commit** `feat: Cognito login and authenticated market-data API`

### Task 19: Watchlist API, view and editor

**Files:**
- Create: `functions/src/handlers/watchlist.ts`, `web/src/watchlist/{useWatchlist.ts,WatchlistEditor.tsx}`
- Modify: `infra/lib/api.ts`, `infra/test/api.test.ts`, `web/src/App.tsx`
- Test: `web/src/watchlist/WatchlistEditor.test.tsx`

**Interfaces:**
- Consumes: `handleWatchlistRequest`, `DynamoWatchlistRepo`, `S3DataStore`.
- Produces: routes `GET /watchlist`, `PUT /watchlist` (JWT) → `stock-heatmap-watchlist` (10 s, 256 MB; `grantReadWriteData` on table, `grantRead` `symbols.json`); handler `userId = event.requestContext.authorizer.jwt.claims.sub`. `useWatchlist(): { items: WatchlistItem[]; save(items): Promise<void>; invalid: WatchlistItem[]; error: string | null }` (query key `["watchlist"]`; on `ApiError` 400 sets `invalid`/`error` and keeps the old list). `WatchlistEditor({ coins: CryptoFile | null, onClose })`: list with remove buttons; add row with type select (`Stock` / `Crypto`) and text input (stock symbol, or CoinGecko id with a `<datalist>` of top-50 `id — SYMBOL`); crypto `symbol` from `coins[id].symbol` else `id.toUpperCase()`; invalid items rendered with `aria-invalid="true"`; `Save` calls `save`. The Watchlist tab uses `buildTiles("watchlist", …)` with these items.

- [ ] **Step 1: Write failing tests:** CDK — routes `GET /watchlist` and `PUT /watchlist` with JWT; watchlist role has `dynamodb:PutItem` and only `symbols.json` read. Editor — add `zzzz` as Stock, Save with mocked 400 `{ error: "Unknown or duplicate items", invalid: [{ type: "stock", symbol: "ZZZZ" }] }` → row `ZZZZ` has `aria-invalid="true"`, error text shown, previously saved items still listed.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and verify** via `npm run dev:remote`: add `PLTR` and `solana`, reload the page on another browser profile → both persist; after the next stock refresh the PLTR tile shows a price (1W/1M/YTD `n/a` until the next baselines run).
- [ ] **Step 6: Commit** `feat: watchlist API, view and editor`

### Task 20: CloudFront site hosting

**Files:**
- Create: `infra/lib/site.ts`, `infra/test/site.test.ts`, `infra/test/fixtures/web-dist/index.html`
- Modify: `infra/lib/stack.ts`, root `package.json` (script `deploy` = `npm run build -w web && cd infra && npx cdk deploy StockHeatmapStack --outputs-file ../cdk-outputs.json`)

**Interfaces:**
- Produces: `Site` construct (props `{ webDistPath: string }`): private `siteBucket` (BLOCK_ALL, DESTROY + autoDelete); `Distribution` with `S3BucketOrigin.withOriginAccessControl`, `REDIRECT_TO_HTTPS`, `CACHING_OPTIMIZED`, `defaultRootObject: "index.html"`, error responses 403/404 → `/index.html` 200 TTL 0. `site.url` = `https://<domainName>`. `site.deploy(config: AppConfig)` adds `BucketDeployment`s (all `prune: false`): `assets/**` with `Cache-Control: public, max-age=31536000, immutable`; `index.html` + `Source.jsonData("config.json", config)` with `Cache-Control: no-cache` and invalidation paths `/index.html`, `/config.json`. Stack order: `Site` → `Api({ siteUrls: [site.url, "http://localhost:5173"] })` → `site.deploy(webConfig)`. `webDistPath` defaults to `../web/dist`; tests pass `test/fixtures/web-dist`. Output `SiteUrl`.

- [ ] **Step 1: Write failing tests:** distribution `ViewerProtocolPolicy: redirect-to-https`; `CustomErrorResponses` for 403 and 404 → `/index.html` 200; an `AWS::CloudFront::OriginAccessControl` exists; site bucket blocks public access; user pool client `CallbackURLs` contains a CloudFront domain token and localhost.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy and verify:** `npm run deploy`; open `SiteUrl` on laptop and phone; with agent-browser run the spec §9 end-to-end checklist (log in, three views render, timeframe switch recolors, tile detail opens, watchlist edit persists after reload).
- [ ] **Step 6: Commit** `feat(infra): CloudFront hosting with runtime config`

### Task 21: Monitoring, alarms and budget

**Files:**
- Create: `infra/lib/monitoring.ts`, `infra/test/monitoring.test.ts`
- Modify: `infra/lib/stack.ts`, `infra/bin/app.ts`

**Interfaces:**
- Produces: `Monitoring` construct (props `{ functions: { stocks, crypto, baselines, metadata }, alertEmail: string }`): SNS topic + `EmailSubscription(alertEmail)`; alarms on `Errors` (Sum, `GREATER_THAN_OR_EQUAL_TO_THRESHOLD` 1, `TreatMissingData.NOT_BREACHING`, action → topic): stocks period 5 min, 3 of 3; crypto period 15 min, 3 of 3; baselines and metadata period 1 h, 1 of 1. `CfnBudget` monthly `COST` 1 USD, notification `FORECASTED` `GREATER_THAN` 100 % to `alertEmail`. `bin/app.ts` reads `process.env.ALERT_EMAIL` and throws `Error("ALERT_EMAIL is required")` when unset.

- [ ] **Step 1: Write failing tests:** 4 `AWS::CloudWatch::Alarm` with the periods/evaluation counts above; budget `BudgetLimit { Amount: 1, Unit: "USD" }` with `FORECASTED` notification; SNS email subscription present.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Deploy:** `ALERT_EMAIL=<you> npm run deploy`; confirm the SNS subscription email. Optional fire drill: set the Alpaca secret parameter to a wrong value during market hours, wait for the alarm email, restore it.
- [ ] **Step 6: Commit** `feat(infra): failure alarms and $1 budget`

---

## Phase 4 — CI/CD (spec §13 step 10)

### Task 22: CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: workflow `ci` on `push` and `pull_request`, job id `ci` on `ubuntu-latest`: checkout → `actions/setup-node@v4` (node 22, npm cache) → `npm ci` → `npm run typecheck` → `npm run lint` → `npm test` → `npm run build -w web` → `cd infra && npx cdk synth` with `env: ALERT_EMAIL: ci@example.invalid`. No AWS credentials.

- [ ] **Step 1: Verify locally first:** run the same command sequence from a clean `npm ci` → all exit 0.
- [ ] **Step 2: Write** the workflow.
- [ ] **Step 3: Publish:** `gh repo create stock-heatmap --public --source . --push`; `gh run watch` → `ci` succeeds.
- [ ] **Step 4: Prove it catches failure:** on a branch, break one assertion, push, confirm `ci` fails; revert and delete the branch.
- [ ] **Step 5: Commit** `ci: typecheck, lint, test, synth on every push`

### Task 23: OIDC deploy role, CD, post-deploy check, branch protection

**Files:**
- Create: `infra/lib/github-oidc-stack.ts`, `infra/test/github-oidc.test.ts`, `scripts/post-deploy-check.sh`
- Modify: `infra/bin/app.ts`, `.github/workflows/ci.yml`

**Interfaces:**
- Produces:
  - `GithubOidcStack` (props `{ repo: string /* "owner/stock-heatmap" from context githubRepo */ }`): `OpenIdConnectProvider` for `https://token.actions.githubusercontent.com` (client id `sts.amazonaws.com`); role `stock-heatmap-github-deploy` trusting it with `StringEquals` `token.actions.githubusercontent.com:aud = sts.amazonaws.com` and `…:sub = repo:<repo>:ref:refs/heads/main`; policy only `sts:AssumeRole` on `arn:aws:iam::<account>:role/cdk-hnb659fds-*-<account>-us-east-1`. Output `DeployRoleArn`. Only instantiated when context `githubRepo` is set.
  - `scripts/post-deploy-check.sh <outputs.json>`: reads `SiteUrl` and `ApiUrl` with `jq`; asserts `curl -fsS $SiteUrl` contains `<div id="root">`; asserts `curl -s -o /dev/null -w '%{http_code}' $ApiUrl/market-data` is `401`; exits non-zero with a message otherwise.
  - `ci.yml` job `deploy`: `needs: ci`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`, `permissions: { id-token: write, contents: read }`, `aws-actions/configure-aws-credentials@v4` with `role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}`, `aws-region: us-east-1`; `npm ci` → `npm run build -w web` → `cd infra && npx cdk deploy StockHeatmapStack --require-approval never --outputs-file ../cdk-outputs.json` with `ALERT_EMAIL: ${{ vars.ALERT_EMAIL }}` → `scripts/post-deploy-check.sh cdk-outputs.json`.

- [ ] **Step 1: Write failing assertion:** role trust policy condition `sub` equals exactly `repo:owner/stock-heatmap:ref:refs/heads/main`; the only allowed action is `sts:AssumeRole`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** stack + script. **Step 4: Run** → PASS; `scripts/post-deploy-check.sh` against outputs from `cdk deploy --outputs-file` → exit 0.
- [ ] **Step 5: One-time setup (human):**
  ```bash
  (cd infra && npx cdk deploy StockHeatmapGithubOidc -c githubRepo=<owner>/stock-heatmap)
  gh variable set AWS_DEPLOY_ROLE_ARN --body <DeployRoleArn>
  gh variable set ALERT_EMAIL --body <you>
  gh api -X PUT repos/<owner>/stock-heatmap/branches/main/protection --input - <<'JSON'
  {"required_status_checks":{"strict":true,"contexts":["ci"]},"enforce_admins":false,"required_pull_request_reviews":null,"restrictions":null}
  JSON
  ```
- [ ] **Step 6: Commit on a branch, open a PR, merge after `ci` passes:** `gh run watch` → `ci` then `deploy` succeed, post-deploy check prints both checks passing.
- [ ] **Step 7: Final end-to-end:** agent-browser run of the spec §9 checklist against `SiteUrl`; confirm the AWS Billing console forecast shows $0.00.
