# Stock Heatmap — Design Spec

- **Date:** 2026-10-04
- **Status:** Approved design, pending spec review
- **Owner:** sseung385

## 1. Purpose and success criteria

A personal market-watch web app that shows, at a glance, how markets are moving
as treemap heatmaps. It is also a hands-on project for learning AWS serverless
and DevOps practices (infrastructure as code, CI/CD), so technology choices
favor real-world AWS patterns over the shortest path.

**Success means:**

1. Opening the hosted URL on phone or laptop shows an accurate heatmap within ~2 seconds.
2. Crypto tiles update live; stock tiles are never more than ~20 minutes behind the market during trading hours (15-minute data delay + 5-minute refresh).
3. Total running cost is **$0/month** (AWS always-free limits and free data-API tiers).
4. The whole system deploys from a single `git push` to `main`.

### Requirements

| # | Requirement |
|---|---|
| R1 | Three views: **S&P 500**, **Watchlist**, **Crypto** |
| R2 | Tiles colored by % change; timeframe switch **1D / 1W / 1M / YTD** |
| R3 | S&P 500 tiles grouped by GICS sector and sized by market cap; crypto (top 50 by market cap) sized by market cap; watchlist tiles equal size |
| R4 | Click a tile → detail panel: price, % change, volume, mini chart |
| R5 | Watchlist (stocks and/or crypto, max 100 items) is editable in the app and synced across devices |
| R6 | Crypto prices stream live; stocks refresh every 5 minutes during market hours |
| R7 | Hosted web app, single user, behind login |
| R8 | $0/month budget |

### Out of scope (v1)

Price alerts, intraday charts, custom domain, multiple users / sign-up, options or
non-US equities, mobile native app, automated end-to-end login tests.

## 2. Architecture

```
                      ┌────────────── Browser (React SPA) ──────────────┐
                      │  Heatmap UI ── Coinbase WebSocket (live crypto)  │
                      └───┬──────────────────────┬──────────────────────┘
          static files    │                      │ JWT (Cognito login)
                 ┌────────▼───────┐      ┌───────▼────────────────┐
                 │ CloudFront+S3  │      │ API Gateway (HTTP API) │
                 └────────────────┘      └───┬──────────────┬─────┘
                                             │              │
                                  GET /market-data   GET/PUT /watchlist
                                             │              │
                                    ┌────────▼───┐    ┌─────▼──────┐
                                    │ S3 (data)  │    │ DynamoDB   │
                                    └────▲───────┘    └────────────┘
                                         │ writes JSON
        EventBridge Scheduler ──► Lambda: refresh-quotes    (stocks 5 min / crypto 15 min)
                              ──► Lambda: refresh-baselines (daily after close)
                              ──► Lambda: refresh-metadata  (weekly)
                                         │
                       Alpaca · CoinGecko · Finnhub · Wikipedia (free tiers)
```

**Principles**

- The browser never calls keyed data APIs. Scheduled Lambdas fetch data and store
  pre-computed JSON in S3; the app reads it through one authenticated endpoint.
  Keys stay server-side and app usage never consumes data-API rate limits.
- Live crypto is the one exception: the browser connects directly to Coinbase's
  public, keyless WebSocket feed. Lambdas cannot hold long-lived connections, and
  this keeps the server out of the live path.
- Each unit has one job. Business logic lives in pure functions; Lambda handlers
  are thin glue so logic is testable and runnable locally.
- Infrastructure is defined in AWS CDK (TypeScript). The whole repo is TypeScript.

## 3. Data sources (free tiers, verified 2026-10-04)

| Source | Used for | Free-tier limits that matter |
|---|---|---|
| **Alpaca Market Data (Basic)** | Stock snapshots (`/v2/stocks/snapshots`), daily bars (`/v2/stocks/bars`), market clock (`/v2/clock`), valid symbols (`/v2/assets`) | 200 calls/min; 7+ years history; full-market SIP data is 15-min delayed — use `feed=delayed_sip` |
| **CoinGecko (Demo)** | Crypto market caps, 24h change, 7-day sparkline (`/coins/markets`), Jan 1 prices (`/coins/{id}/history`), coin list (`/coins/list`) | 10,000 calls/month; attribution required |
| **Coinbase Exchange WebSocket** | Live crypto prices (`ticker` channel), from the browser | Public, no key |
| **Finnhub** | Shares outstanding (`/stock/profile2`) | 60 calls/min |
| **Wikipedia** | S&P 500 constituents and GICS sectors ("List of S&P 500 companies" table) | Scraped weekly |

Estimated monthly calls: CoinGecko ≈ 2,900–5,800 (crypto refresh every 15 min, 1–2
calls) + ~5 (weekly coin list) — under 10,000. Alpaca ≈ 7 calls per 5-minute run.

## 4. Data pipeline

### 4.1 Schedules (EventBridge Scheduler, timezone `America/New_York`)

| Job | Schedule | Work |
|---|---|---|
| `refresh-quotes` (scope `stocks`) | Every 5 min, Mon–Fri 09:30–16:20 ET | `GET /v2/clock`; if market closed and last write was after the close, exit. Otherwise fetch snapshots for universe in batches of 100 (~6 calls) → `stocks.json` |
| `refresh-quotes` (scope `crypto`) | Every 15 min, 24/7 | CoinGecko `/coins/markets` for top 50, plus a second call by `ids` for watchlist coins outside the top 50 (1–2 calls), with sparkline and 7d/30d change → `crypto.json` |
| `refresh-baselines` | Mon–Fri 17:00 ET | Alpaca daily bars from previous Dec 31 (minus 7 days) to today for all stock tickers (paged) → baselines; CoinGecko Jan 1 price per coin, fetched only if missing for the current year → `baselines.json` |
| `refresh-metadata` | Sundays 06:00 ET | Wikipedia S&P list + sectors; Finnhub `profile2` for each ticker paced at 55/min (~9 min, under Lambda's 15-min max); → `metadata.json`; Alpaca `/v2/assets` (valid stock symbols) and CoinGecko `/coins/list` (valid coin ids) → `symbols.json` |

**Stock universe** = S&P 500 constituents ∪ stock tickers on the watchlist.
`refresh-quotes` reads the watchlist from DynamoDB (read-only).

### 4.2 Files in the data bucket

All files carry a top-level `asOf` (ISO-8601 UTC) and per-entry `asOf` where
values may be carried over from a previous run.

- `stocks.json` — `{ asOf, quotes: { [symbol]: { price, prevClose, volume, asOf } } }`
- `crypto.json` — `{ asOf, coins: { [id]: { symbol, name, price, change24h, change7d, change30d, marketCap, sparkline7d: number[], coinbaseProductId | null, asOf } } }`
- `baselines.json` — `{ asOf, stocks: { [symbol]: { close1W, close1M, closeYearEnd, closes30: number[] } }, crypto: { [id]: { priceJan1, year } } }`
- `metadata.json` — `{ asOf, sp500: [{ symbol, name, sector }], sharesOutstanding: { [symbol]: number } }`
- `symbols.json` — `{ asOf, validStockSymbols: string[], validCoinIds: string[] }` (large; read only by the `watchlist` Lambda, never sent to the browser)

### 4.3 Computations

- **Baselines (stocks):** `close1W` = close 5 trading days before the latest close;
  `close1M` = close 21 trading days before; `closeYearEnd` = last close of the
  previous calendar year; `closes30` = last 30 daily closes. Trading days are taken
  from the returned bars, so holidays are handled implicitly.
- **% change (browser):** `change = price / baseline − 1`, where baseline is
  `prevClose` (1D), `close1W`, `close1M` or `closeYearEnd` (YTD).
- **Crypto:** 1D uses rolling 24h (`change24h`, recomputed against the live price
  when streaming). 1W/1M derive a baseline from CoinGecko's `change7d` / `change30d`
  (`baseline = price / (1 + change)`, using the CoinGecko price at fetch time) so the
  live price can be compared against it; YTD uses `priceJan1`.
- **Tile size:** stocks `price × sharesOutstanding`; crypto `marketCap`; watchlist equal.
- **Color scale:** diverging red → neutral gray → green, clamped at ±3% (1D),
  ±6% (1W), ±10% (1M), ±20% (YTD).
- Coins without a Coinbase USD product (e.g. BNB) use the CoinGecko price and are
  labelled "delayed" in the UI.

## 5. API, authentication and storage

### 5.1 Authentication (Cognito)

- User pool with self-sign-up **disabled**; the single user is created with an
  admin CLI command printed as a CDK output. Email + password; optional TOTP MFA
  (recommended).
- Managed login (hosted UI), authorization-code flow with PKCE, public app client.
  Callback/logout URLs: the CloudFront URL and `http://localhost:5173`.
- Access token 1 hour; refresh token 30 days.

### 5.2 API (API Gateway HTTP API)

- JWT authorizer bound to the user pool and app client; every route requires it.
- CORS: CloudFront origin and `http://localhost:5173` only.

| Route | Handler | Behavior |
|---|---|---|
| `GET /market-data` | `get-market-data` | Read the 4 data files from S3 in parallel; return merged `{ stocks, crypto, baselines, metadata }` with `Cache-Control: private, max-age=30`. ~60 KB gzipped |
| `GET /watchlist` | `watchlist` | Return `{ items: WatchlistItem[], updatedAt }` (empty list if none) |
| `PUT /watchlist` | `watchlist` | Validate and replace the whole list; return saved list, or `400 { invalid: WatchlistItem[] }` |

`WatchlistItem = { type: "stock", symbol: string } | { type: "crypto", id: string, symbol: string }`

**Validation:** ≤ 100 items, no duplicates; stock symbols ∈ `validStockSymbols`;
coin ids ∈ `validCoinIds` (both from `symbols.json`). Last write wins (single user; no concurrency control).

### 5.3 Storage

- **DynamoDB** table `Watchlist`: partition key `userId` (Cognito `sub`); attributes
  `items`, `updatedAt`. Provisioned capacity 1 RCU / 1 WCU (covered by always-free).
- **S3**: private `site` bucket (served via CloudFront) and private `data` bucket.
  Both block all public access.
- **Secrets**: Alpaca key/secret, Finnhub key and CoinGecko key in SSM Parameter
  Store `SecureString` (free), read by Lambdas at cold start.

### 5.4 IAM (least privilege)

- Each refresh Lambda may `PutObject` only its own keys (`refresh-quotes`: `stocks.json`,
  `crypto.json`; `refresh-baselines`: `baselines.json`; `refresh-metadata`: `metadata.json`,
  `symbols.json`) and `GetObject` only the files it reads (its previous output and
  `metadata.json`).
- `get-market-data` may only `GetObject` `stocks.json`, `crypto.json`, `baselines.json`,
  `metadata.json`.
- `watchlist` has read/write on the `Watchlist` table and `GetObject` on `symbols.json`;
  `refresh-quotes` and `refresh-baselines` have read-only access to the table.
- Each Lambda may read only the SSM parameters it needs.

### 5.5 CloudFront

- Origin: `site` bucket via Origin Access Control; HTTPS-only (redirect).
- `index.html`: no-cache. Hashed assets (`assets/*`): cached 1 year.
- 403/404 → `/index.html` (200) for SPA routing.
- Default `*.cloudfront.net` domain (custom domain out of scope).

## 6. Frontend

**Stack:** React + TypeScript + Vite; `d3-hierarchy` (squarified treemap) rendered
as SVG; TanStack Query; `react-oidc-context` for Cognito.

**Layout:** top bar with view tabs (S&P 500 / Watchlist / Crypto), timeframe toggle
(1D/1W/1M/YTD), "Edit list" button, and a status line ("Stocks as of 14:35 ET ·
Crypto live ●"). Main area is the treemap; S&P view shows sector group labels.
Clicking a tile opens a side panel. Footer: "Crypto data by CoinGecko".

| Unit | Responsibility | Depends on |
|---|---|---|
| `AuthGate` | Redirect to login when signed out; provide token to API calls | Cognito |
| `useMarketData()` | Poll `GET /market-data` every 60 s | API |
| `useCryptoStream(productIds)` | Coinbase WebSocket; reconnect with backoff 1 s → 30 s; throttle UI updates to 1/s | Coinbase |
| `computeChange(quote, baseline, timeframe)` | Pure % change calculation | — |
| `layoutTreemap(items, groupBy, width, height)` | Pure tile-rectangle calculation | d3-hierarchy |
| `Heatmap` | Render tiles and color scale | the two pure functions |
| `TileDetail` | Price, % change, volume, mini chart (stocks: `closes30` + current; crypto: `sparkline7d`) | snapshot data |
| `WatchlistEditor` | Add/remove items; show validation errors | `/watchlist` API |

## 7. Error handling and monitoring

**Rule: a bad run never overwrites good data.**

- External calls retry 3× with exponential backoff on 429/5xx.
- `refresh-quotes` merges with the previous file: failed tickers keep previous
  values and their own older `asOf`. The file is written only if ≥ 90% of tickers
  are fresh; otherwise the run fails and the last good file remains.
- `refresh-metadata` accepts the Wikipedia scrape only if it yields 495–510 rows
  across all 11 GICS sectors; otherwise it keeps the previous metadata and fails.
- Tickers missing a baseline (new IPO, new watchlist entry) show a gray "n/a" tile
  for that timeframe until the next baseline run.

**Alerts (free tier):**

- CloudWatch alarm → SNS email when `refresh-quotes` fails 3 consecutive runs, or
  `refresh-baselines` / `refresh-metadata` fails once.
- AWS Budgets alert at **$1** forecast monthly spend.
- Structured JSON logs; 14-day retention.

**Frontend behavior:**

| Situation | UI |
|---|---|
| Stock data > 25 min old during market hours | Yellow banner "Stock data delayed since HH:MM ET"; tiles still shown |
| Market closed | "Market closed · as of <last close>" (not an error) |
| `/market-data` fails | Keep last data; "Retrying…" indicator; next poll retries |
| Token expired | Silent refresh; on failure redirect to login |
| Coinbase disconnect | "Reconnecting…" status; tiles fall back to cached CoinGecko price |
| Watchlist save rejected | Highlight invalid entries; nothing saved |

## 8. Repository layout

```
stock-heatmap/
├── shared/          shared types: MarketData, WatchlistItem, Timeframe
├── functions/
│   ├── core/        pure logic (baselines, merge, validation, scrape parsing)
│   ├── clients/     Alpaca, CoinGecko, Finnhub, Wikipedia wrappers
│   └── handlers/    Lambda entry points (glue only)
├── web/             React app
├── infra/           AWS CDK app
└── .github/workflows/
```

npm workspaces; TypeScript everywhere.

**Local development**

- `npm run refresh` — runs the refresh logic on the Mac, writing JSON to `.data/`.
- `npm run dev` — Vite dev server against a local mock API that serves `.data/`
  (no AWS required). Live crypto still streams from Coinbase.

## 9. Testing

| Level | Tool | Covers |
|---|---|---|
| Unit | Vitest | `computeChange`, `layoutTreemap`, baseline derivation (holidays, year boundary), merge + 90% rule, watchlist validation, Wikipedia parsing against saved HTML |
| Client | Vitest + recorded fixtures | Each API client parses real recorded responses; no live calls |
| Live smoke | `npm run smoke` (manual) | One real call per external API to catch upstream changes |
| Infrastructure | CDK assertions | Buckets private, IAM scoped as in §5.4, DynamoDB 1/1, alarms and budget exist |
| Post-deploy | CD pipeline | CloudFront URL returns 200 with the new build; `GET /market-data` without a token returns 401 (authorizer active). No user credentials stored in CI |
| End-to-end | agent-browser (manual, after deploys) | Login; 3 views render; timeframe switch recolors; tile detail opens; watchlist edit persists after reload |

## 10. CI/CD (GitHub Actions)

- **CI** on every push and pull request: `npm ci` → typecheck → lint → unit/client
  tests → `cdk synth`. No AWS credentials.
- **CD** on push to `main` (after CI passes): `cdk deploy --all`, then the
  post-deploy smoke check.
- **AWS access via OIDC**: a one-time IAM role trusts GitHub's OIDC provider for
  `repo:<owner>/stock-heatmap:ref:refs/heads/main` only, with deploy permissions
  (assume the CDK bootstrap roles). No long-lived AWS keys in GitHub.
- **Branch protection** on `main`: CI must pass before merging.
- **One-time bootstrap from the Mac**: `cdk bootstrap`; deploy the OIDC role stack;
  store data-API keys in SSM; create the Cognito user.
- Private GitHub repo; expected Actions usage ~100 of 2,000 free minutes/month.

## 11. Cost

| Item | Expected | Covered by |
|---|---|---|
| Lambda | ~2,500 scheduled + few hundred API invocations/month | Always-free 1M requests, 400k GB-s |
| S3 | < 10 MB, few thousand requests | Free tier / cents at most |
| CloudFront | Personal traffic | Always-free 1 TB, 10M requests |
| API Gateway HTTP API | Few thousand requests/month | Free tier (new accounts); ~$1/million after — effectively $0 |
| DynamoDB | 1 RCU / 1 WCU | Always-free 25 RCU/WCU, 25 GB |
| Cognito | 1 user | Free up to 10k MAU |
| EventBridge Scheduler, SSM Standard, CloudWatch (≤10 alarms), Budgets (≤2) | — | Free |
| Data APIs, GitHub Actions | — | Free tiers |

Guardrail: $1 budget alert (§7).

## 12. AWS concepts used (learning notes)

| Concept | Where | Why |
|---|---|---|
| **Lambda** | All backend logic | Runs code on demand with no server; this app computes only minutes per day, so pay-per-use is $0 |
| **EventBridge Scheduler** | Triggers refresh jobs | Managed cron in AWS that runs whether or not your laptop is on |
| **S3** | Website files and data snapshots | Durable file storage; data is whole-file snapshots replaced on each run |
| **CloudFront** | Serves the web app | CDN that adds HTTPS and is the only reader of the private site bucket |
| **API Gateway** | `/market-data`, `/watchlist` | HTTPS front door for Lambdas; validates JWTs before code runs |
| **Cognito** | Login | Managed user directory issuing JWTs |
| **DynamoDB** | Watchlist | Key-value database for small records edited in place |
| **SSM Parameter Store** | API keys | Free encrypted secret storage |
| **IAM** | Every component | Least-privilege permissions per Lambda; OIDC role for GitHub |
| **CloudWatch / SNS / Budgets** | Monitoring | Logs, failure alarms by email, cost guardrail |
| **CDK** | `infra/` | All of the above defined as TypeScript code |

## 13. Suggested build order

Each step introduces one main concept and ends with something working:

1. Shared types + `functions/core` + clients with tests; `npm run refresh` writes `.data/` locally.
2. React heatmap against the local mock API (S&P view, timeframe switch, tile detail).
3. Crypto view + Coinbase live stream.
4. CDK: data bucket + `refresh-quotes` Lambda + schedule (first deploy).
5. Baselines and metadata Lambdas.
6. Cognito + API Gateway + `get-market-data`; frontend switches to the real API.
7. DynamoDB + watchlist API + editor.
8. CloudFront + site bucket (hosted app).
9. Monitoring, alarms, budget.
10. CI workflow; then OIDC role + CD workflow + branch protection.
