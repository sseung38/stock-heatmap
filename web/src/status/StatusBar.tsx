import { formatEtTime, type StocksFile } from "@sh/shared";
import type { StreamStatus } from "../crypto/useCryptoStream";
import { stockStatus } from "./marketStatus";

/** @param crypto live-stream status, or null when there are no coins to stream */
export function StatusBar({ stocks, crypto, isError }: { stocks: StocksFile | null; crypto: StreamStatus | null; isError: boolean }) {
  const status = stockStatus(stocks, new Date());
  return (
    <div className="status-bar">
      {status.kind === "ok" && <span>Stocks as of {formatEtTime(status.asOf)}</span>}
      {status.kind === "closed" && <span>Market closed · as of {formatEtTime(status.asOf)}</span>}
      {status.kind === "delayed" && (
        <span className="banner-delayed" role="status">Stock data delayed since {formatEtTime(status.since)}</span>
      )}
      {crypto === "live" && <span className="crypto-live">Crypto live ●</span>}
      {crypto === "reconnecting" && <span>Reconnecting…</span>}
      {isError && <span className="retrying">Retrying…</span>}
    </div>
  );
}
