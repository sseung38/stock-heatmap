import type { CoinQuote, MarketData } from "@sh/shared";
import { describe, expect, it } from "vitest";
import { buildTiles } from "./buildTiles";

const coin = (symbol: string, marketCap: number, productId: string | null): CoinQuote => ({
  symbol, name: symbol, price: 100, change24h: 0, change7d: 0, change30d: 0,
  marketCap, sparkline7d: [99, 100], coinbaseProductId: productId, asOf: "",
});

const data: MarketData = {
  stocks: {
    asOf: "2026-10-02T18:35:00Z",
    market: { isOpen: true },
    quotes: {
      AAPL: { price: 110, prevClose: 100, volume: 5, tradingDay: "2026-10-02", asOf: "" },
      NEWCO: { price: 20, prevClose: 20, volume: 1, tradingDay: "2026-10-02", asOf: "" },
    },
  },
  crypto: {
    asOf: "",
    coins: {
      bitcoin: coin("BTC", 2e12, "BTC-USD"),
      binancecoin: coin("BNB", 9e10, null),
    },
  },
  baselines: {
    asOf: "",
    stocks: { AAPL: { closes: [90, 95, 100], lastCloseDate: "2026-10-01", yearEndCloses: {} } },
    crypto: {},
  },
  metadata: {
    asOf: "",
    sp500: [
      { symbol: "AAPL", name: "Apple Inc.", sector: "Information Technology" },
      { symbol: "NEWCO", name: "New Co", sector: "Industrials" },
      { symbol: "GONE", name: "No Quote", sector: "Energy" },
    ],
    sharesOutstanding: { AAPL: 1e9 },
  },
};

describe("buildTiles", () => {
  it("builds S&P tiles sized by market cap and grouped by sector", () => {
    const tiles = buildTiles("sp500", data, {}, [], "1D", 2026);

    expect(tiles.map((t) => t.id)).toEqual(["AAPL", "NEWCO"]); // GONE has no quote
    const aapl = tiles[0]!;
    expect(aapl).toMatchObject({ label: "AAPL", name: "Apple Inc.", group: "Information Technology", size: 110e9, price: 110, volume: 5, delayed: false });
    expect(aapl.change).toBeCloseTo(0.1);
    expect(aapl.spark).toEqual([90, 95, 100, 110]);
  });

  it("keeps a tile whose share count is unknown (size 0, laid out at minimum size)", () => {
    expect(buildTiles("sp500", data, {}, [], "1D", 2026).find((t) => t.id === "NEWCO")!.size).toBe(0);
  });

  it("uses live prices for crypto and marks coins without a Coinbase stream as delayed", () => {
    const tiles = buildTiles("crypto", data, { "BTC-USD": 110 }, [], "1D", 2026);

    const btc = tiles.find((t) => t.id === "bitcoin")!;
    expect(btc.price).toBe(110);
    expect(btc.change).toBeCloseTo(0.1); // baseline = 100 / (1 + 0)
    expect(btc.delayed).toBe(false);
    expect(tiles.find((t) => t.id === "binancecoin")!.delayed).toBe(true);
  });

  it("shows only the top 50 coins by market cap", () => {
    const coins: Record<string, CoinQuote> = {};
    for (let i = 0; i < 60; i++) coins[`c${i}`] = coin(`C${i}`, i, null);

    const tiles = buildTiles("crypto", { ...data, crypto: { asOf: "", coins } }, {}, [], "1D", 2026);

    expect(tiles).toHaveLength(50);
    expect(tiles.map((t) => t.id)).not.toContain("c0");
  });

  it("builds equal-size watchlist tiles in list order, with n/a for missing data", () => {
    const tiles = buildTiles("watchlist", data, {}, [
      { type: "crypto", id: "bitcoin", symbol: "BTC" },
      { type: "stock", symbol: "PLTR" },
      { type: "stock", symbol: "AAPL" },
    ], "1W", 2026);

    expect(tiles.map((t) => t.id)).toEqual(["bitcoin", "PLTR", "AAPL"]);
    expect(tiles.every((t) => t.size === 1 && t.group === null)).toBe(true);
    expect(tiles[1]).toMatchObject({ label: "PLTR", price: null, change: null });
    expect(tiles[2]!.change).toBeNull(); // only 3 closes: not enough history for 1W
  });

  it("returns nothing before the first refresh", () => {
    const empty: MarketData = { stocks: null, crypto: null, baselines: null, metadata: null };

    expect(buildTiles("sp500", empty, {}, [], "1D", 2026)).toEqual([]);
    expect(buildTiles("crypto", empty, {}, [], "1D", 2026)).toEqual([]);
    expect(buildTiles("watchlist", empty, {}, [{ type: "stock", symbol: "AAPL" }], "1D", 2026)[0]!.change).toBeNull();
  });
});
