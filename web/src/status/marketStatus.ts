import { etDate, type StocksFile } from "@sh/shared";

export type StockStatus =
  | { kind: "none" }
  | { kind: "closed"; tradingDay: string }
  | { kind: "delayed"; since: string }
  | { kind: "ok"; asOf: string };

/** Data older than this during market hours means refreshes have stopped (normal age is ≤ 20 min). */
export const DELAYED_AFTER_MS = 25 * 60 * 1000;

export function stockStatus(stocks: StocksFile | null, now: Date): StockStatus {
  if (!stocks) return { kind: "none" };
  if (!stocks.market.isOpen) return { kind: "closed", tradingDay: lastTradingDay(stocks) };
  if (now.getTime() - Date.parse(stocks.asOf) > DELAYED_AFTER_MS) return { kind: "delayed", since: stocks.asOf };
  return { kind: "ok", asOf: stocks.asOf };
}

/** The newest session among the quotes (yyyy-MM-dd); falls back to the file's own date. */
function lastTradingDay(stocks: StocksFile): string {
  const days = Object.values(stocks.quotes).map((q) => q.tradingDay);
  return days.length > 0 ? days.reduce((a, b) => (a > b ? a : b)) : etDate(stocks.asOf);
}

/** "Fri, Oct 2" for "2026-10-02". */
export function formatTradingDay(day: string): string {
  return new Date(`${day}T12:00:00Z`).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric", timeZone: "UTC" });
}
