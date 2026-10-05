import type { CoinQuote, StockBaseline, StockQuote } from "@sh/shared";
import { describe, expect, it } from "vitest";
import { coinBaselineFor, computeChange, stockBaselineFor } from "./change";

const baseline: StockBaseline = {
  closes: Array.from({ length: 30 }, (_, i) => i + 1), // 1..30, oldest first
  lastCloseDate: "2026-10-01",
  yearEndCloses: { "2025": 50 },
};
const quote = (tradingDay: string): StockQuote => ({ price: 40, prevClose: 30, volume: 1, tradingDay, asOf: "" });
const coin: CoinQuote = {
  symbol: "BTC", name: "Bitcoin", price: 110, change24h: 0.1, change7d: 0.1, change30d: null,
  marketCap: 1, sparkline7d: [], coinbaseProductId: "BTC-USD", asOf: "",
};

describe("stockBaselineFor", () => {
  it("counts sessions back from the quote's trading day", () => {
    expect(stockBaselineFor(quote("2026-10-02"), baseline, "1W")).toBe(26);
    expect(stockBaselineFor(quote("2026-10-02"), baseline, "1M")).toBe(10);
  });

  it("skips the baseline's own close when it is the quote's trading day", () => {
    expect(stockBaselineFor(quote("2026-10-01"), baseline, "1W")).toBe(25);
  });

  it("uses the previous close for 1D", () => {
    expect(stockBaselineFor(quote("2026-10-02"), baseline, "1D")).toBe(30);
  });

  it("uses last year's final close for YTD, including early January", () => {
    expect(stockBaselineFor(quote("2026-01-02"), { ...baseline, lastCloseDate: "2025-12-31" }, "YTD")).toBe(50);
    expect(stockBaselineFor(quote("2027-01-04"), baseline, "YTD")).toBeNull();
  });

  it("returns null when history is too short or missing", () => {
    expect(stockBaselineFor(quote("2026-10-02"), { ...baseline, closes: [1, 2, 3] }, "1M")).toBeNull();
    expect(stockBaselineFor(quote("2026-10-02"), undefined, "1W")).toBeNull();
  });
});

describe("coinBaselineFor", () => {
  it("derives a baseline from the percent change", () => {
    expect(coinBaselineFor(coin, undefined, "1W", 2026)).toBeCloseTo(100);
    expect(coinBaselineFor(coin, undefined, "1D", 2026)).toBeCloseTo(100);
    expect(coinBaselineFor(coin, undefined, "1M", 2026)).toBeNull();
  });

  it("uses the Jan 1 price only for the current year", () => {
    expect(coinBaselineFor(coin, { priceJan1: 90, year: 2026 }, "YTD", 2026)).toBe(90);
    expect(coinBaselineFor(coin, { priceJan1: 90, year: 2025 }, "YTD", 2026)).toBeNull();
  });
});

describe("computeChange", () => {
  it("returns the fractional change", () => {
    expect(computeChange(110, 100)).toBeCloseTo(0.1);
  });

  it("returns null without a usable baseline", () => {
    expect(computeChange(110, null)).toBeNull();
    expect(computeChange(null, 100)).toBeNull();
    expect(computeChange(110, 0)).toBeNull();
  });
});
