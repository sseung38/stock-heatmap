import type { StocksFile } from "@sh/shared";
import { expect, it } from "vitest";
import { formatTradingDay, stockStatus } from "./marketStatus";

const stocks = (isOpen: boolean, asOf: string): StocksFile => ({
  asOf,
  market: { isOpen },
  quotes: {
    AAPL: { price: 1, prevClose: 1, volume: 1, tradingDay: "2026-10-02", asOf: "" },
    MSFT: { price: 1, prevClose: 1, volume: 1, tradingDay: "2026-10-01", asOf: "" },
  },
});
const NOW = new Date("2026-10-02T18:40:00Z");

it("reports a closed market with its latest trading day, not the time the file was written", () => {
  expect(stockStatus(stocks(false, "2026-10-05T05:43:00Z"), NOW)).toEqual({ kind: "closed", tradingDay: "2026-10-02" });
});

it("formats the last session as a short weekday and date", () => {
  expect(formatTradingDay("2026-10-02")).toBe("Fri, Oct 2");
});

it("flags open-market data older than 25 minutes as delayed", () => {
  expect(stockStatus(stocks(true, "2026-10-02T18:14:00Z"), NOW)).toEqual({ kind: "delayed", since: "2026-10-02T18:14:00Z" });
});

it("treats recent open-market data as current", () => {
  expect(stockStatus(stocks(true, "2026-10-02T18:30:00Z"), NOW)).toEqual({ kind: "ok", asOf: "2026-10-02T18:30:00Z" });
});

it("has nothing to report before the first refresh", () => {
  expect(stockStatus(null, NOW)).toEqual({ kind: "none" });
});
