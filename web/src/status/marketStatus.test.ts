import type { StocksFile } from "@sh/shared";
import { expect, it } from "vitest";
import { stockStatus } from "./marketStatus";

const stocks = (isOpen: boolean, asOf: string): StocksFile => ({ asOf, market: { isOpen }, quotes: {} });
const NOW = new Date("2026-10-02T18:40:00Z");

it("reports a closed market as closed, however old the data", () => {
  expect(stockStatus(stocks(false, "2026-10-02T20:20:00Z"), NOW)).toEqual({ kind: "closed", asOf: "2026-10-02T20:20:00Z" });
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
