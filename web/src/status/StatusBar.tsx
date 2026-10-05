import { formatEtTime, type StocksFile } from "@sh/shared";
import { stockStatus } from "./marketStatus";

export function StatusBar({ stocks, isError }: { stocks: StocksFile | null; isError: boolean }) {
  const status = stockStatus(stocks, new Date());
  return (
    <div className="status-bar">
      {status.kind === "ok" && <span>Stocks as of {formatEtTime(status.asOf)}</span>}
      {status.kind === "closed" && <span>Market closed · as of {formatEtTime(status.asOf)}</span>}
      {status.kind === "delayed" && (
        <span className="banner-delayed" role="status">Stock data delayed since {formatEtTime(status.since)}</span>
      )}
      {isError && <span className="retrying">Retrying…</span>}
    </div>
  );
}
