// TypeScript mirror of the Java records in backend/core/.../model. Kept in sync by the
// contract tests on both sides (contract/*.json). Percent changes are fractions (0.025 = 2.5%).

export type Timeframe = "1D" | "1W" | "1M" | "YTD";
export const TIMEFRAMES: Timeframe[] = ["1D", "1W", "1M", "YTD"];

export interface StockQuote {
  price: number;
  prevClose: number;
  volume: number;
  /** New York date (yyyy-MM-dd) of the session the price belongs to. */
  tradingDay: string;
  asOf: string;
}

export interface StocksFile {
  asOf: string;
  market: { isOpen: boolean };
  quotes: Record<string, StockQuote>;
}

export interface CoinQuote {
  symbol: string;
  name: string;
  price: number;
  change24h: number | null;
  change7d: number | null;
  change30d: number | null;
  marketCap: number;
  sparkline7d: number[];
  /** Coinbase product for live prices (e.g. "BTC-USD"), or null when only delayed prices exist. */
  coinbaseProductId: string | null;
  asOf: string;
}

export interface CryptoFile {
  asOf: string;
  coins: Record<string, CoinQuote>;
}

export interface StockBaseline {
  /** Up to 30 daily closes, oldest first; the newest is from lastCloseDate. */
  closes: number[];
  lastCloseDate: string;
  /** Last close of each calendar year, keyed by year ("2025"). */
  yearEndCloses: Record<string, number>;
}

export interface CoinBaseline {
  priceJan1: number;
  year: number;
}

export interface BaselinesFile {
  asOf: string;
  stocks: Record<string, StockBaseline>;
  crypto: Record<string, CoinBaseline>;
}

export interface Sp500Entry {
  symbol: string;
  name: string;
  sector: string;
}

export interface MetadataFile {
  asOf: string;
  sp500: Sp500Entry[];
  sharesOutstanding: Record<string, number>;
}

export type WatchlistItem = { type: "stock"; symbol: string } | { type: "crypto"; id: string; symbol: string };

export interface Watchlist {
  items: WatchlistItem[];
  updatedAt: string | null;
}

/** GET /market-data. A field is null until its refresh job has written the file once. */
export interface MarketData {
  stocks: StocksFile | null;
  crypto: CryptoFile | null;
  baselines: BaselinesFile | null;
  metadata: MetadataFile | null;
}

/** /config.json, written by CDK next to the deployed site (or served by the local mock API). */
export interface AppConfig {
  apiUrl: string;
  /** null locally: no login. */
  auth: { authority: string; clientId: string } | null;
}
