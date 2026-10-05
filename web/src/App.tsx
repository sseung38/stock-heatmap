import { etDate, TIMEFRAMES, type MarketData, type Timeframe } from "@sh/shared";
import { useMemo, useState } from "react";
import { useMarketData } from "./api/useMarketData";
import { useCryptoStream } from "./crypto/useCryptoStream";
import { TileDetail } from "./detail/TileDetail";
import { buildTiles, type Tile, type View } from "./heatmap/buildTiles";
import { Heatmap } from "./heatmap/Heatmap";
import { StatusBar } from "./status/StatusBar";

const VIEWS: { id: View; label: string }[] = [
  { id: "sp500", label: "S&P 500" },
  { id: "watchlist", label: "Watchlist" },
  { id: "crypto", label: "Crypto" },
];

const isEmpty = (d: MarketData) => !d.stocks && !d.crypto && !d.baselines && !d.metadata;

export default function App() {
  const { data, isError } = useMarketData();
  const [view, setView] = useState<View>("sp500");
  const [tf, setTf] = useState<Timeframe>("1D");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // Stream every coin that has a Coinbase product; the joined key keeps the socket open across polls.
  const productIds = useMemo(
    () => Object.values(data?.crypto?.coins ?? {}).flatMap((c) => (c.coinbaseProductId ? [c.coinbaseProductId] : [])).sort(),
    [data?.crypto],
  );
  const stream = useCryptoStream(productIds);

  const year = Number(etDate(new Date().toISOString()).slice(0, 4));
  const tiles: Tile[] = data ? buildTiles(view, data, stream.prices, [], tf, year) : [];
  const selected = tiles.find((t) => t.id === selectedId) ?? null;

  return (
    <div className="app">
      <header>
        <nav className="tabs">
          {VIEWS.map((v) => (
            <button key={v.id} aria-pressed={view === v.id} onClick={() => { setView(v.id); setSelectedId(null); }}>
              {v.label}
            </button>
          ))}
        </nav>
        <div className="timeframes">
          {TIMEFRAMES.map((t) => (
            <button key={t} aria-pressed={tf === t} onClick={() => setTf(t)}>
              {t}
            </button>
          ))}
        </div>
        <button className="edit-list">Edit list</button>
      </header>
      <StatusBar stocks={data?.stocks ?? null} crypto={productIds.length > 0 ? stream.status : null} isError={isError} />
      <main>
        {!data && <p className="empty">Loading…</p>}
        {data && isEmpty(data) && <p className="empty">Waiting for first data refresh</p>}
        {data && !isEmpty(data) && (
          <Heatmap tiles={tiles} grouped={view === "sp500"} tf={tf} onSelect={(t) => setSelectedId(t.id)} />
        )}
        {selected && <TileDetail tile={selected} onClose={() => setSelectedId(null)} />}
      </main>
      <footer>Crypto data by CoinGecko</footer>
    </div>
  );
}
