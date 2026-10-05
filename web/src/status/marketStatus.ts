import type { StocksFile } from "@sh/shared";

export type StockStatus =
  | { kind: "none" }
  | { kind: "closed"; asOf: string }
  | { kind: "delayed"; since: string }
  | { kind: "ok"; asOf: string };

/** Data older than this during market hours means refreshes have stopped (normal age is ≤ 20 min). */
export const DELAYED_AFTER_MS = 25 * 60 * 1000;

export function stockStatus(stocks: StocksFile | null, now: Date): StockStatus {
  if (!stocks) return { kind: "none" };
  if (!stocks.market.isOpen) return { kind: "closed", asOf: stocks.asOf };
  if (now.getTime() - Date.parse(stocks.asOf) > DELAYED_AFTER_MS) return { kind: "delayed", since: stocks.asOf };
  return { kind: "ok", asOf: stocks.asOf };
}
