import type { CoinBaseline, CoinQuote, StockBaseline, StockQuote, Timeframe } from "@sh/shared";

/** Fractional change (0.1 = +10%), or null when either side is missing. */
export function computeChange(price: number | null, baseline: number | null): number | null {
  if (price === null || baseline === null || baseline <= 0) return null;
  return price / baseline - 1;
}

/**
 * The close to compare a stock's price against, counted in sessions back from the quote's own
 * trading day (spec §14.3): 1W = 5 sessions, 1M = 21 sessions, YTD = last year's final close.
 */
export function stockBaselineFor(quote: StockQuote, baseline: StockBaseline | undefined, tf: Timeframe): number | null {
  if (tf === "1D") return quote.prevClose;
  if (!baseline) return null;
  if (tf === "YTD") {
    const lastYear = String(Number(quote.tradingDay.slice(0, 4)) - 1);
    return baseline.yearEndCloses[lastYear] ?? null;
  }
  const prior = priorCloses(quote, baseline);
  const sessionsBack = tf === "1W" ? 5 : 21;
  return prior.length >= sessionsBack ? (prior[prior.length - sessionsBack] ?? null) : null;
}

/** Daily closes strictly before the quote's trading day, oldest first. */
export function priorCloses(quote: StockQuote, baseline: StockBaseline | undefined): number[] {
  if (!baseline) return [];
  return quote.tradingDay > baseline.lastCloseDate ? baseline.closes : baseline.closes.slice(0, -1);
}

/** Rolling windows for crypto: the baseline is implied by CoinGecko's percent change at fetch time. */
export function coinBaselineFor(coin: CoinQuote, baseline: CoinBaseline | undefined, tf: Timeframe, year: number): number | null {
  if (tf === "YTD") return baseline && baseline.year === year ? baseline.priceJan1 : null;
  const change = tf === "1D" ? coin.change24h : tf === "1W" ? coin.change7d : coin.change30d;
  return change === null ? null : coin.price / (1 + change);
}
