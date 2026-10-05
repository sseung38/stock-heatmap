import type { MarketData, StockQuote, Timeframe, WatchlistItem } from "@sh/shared";
import { coinBaselineFor, computeChange, priorCloses, stockBaselineFor } from "./change";

export type View = "sp500" | "watchlist" | "crypto";

export interface Tile {
  id: string;
  label: string;
  name: string;
  group: string | null;
  size: number;
  price: number | null;
  /** Fractional change for the selected timeframe, or null ("n/a"). */
  change: number | null;
  volume: number | null;
  /** True when the price is not streamed live (crypto without a Coinbase product). */
  delayed: boolean;
  /** Values for the mini chart, oldest first, ending at the current price. */
  spark: number[];
}

const TOP_COINS = 50;

/** Live crypto prices keyed by Coinbase product id ("BTC-USD"). */
export type LivePrices = Record<string, number>;

export function buildTiles(view: View, data: MarketData, live: LivePrices, watchlist: WatchlistItem[], tf: Timeframe, year: number): Tile[] {
  switch (view) {
    case "sp500":
      return (data.metadata?.sp500 ?? []).flatMap((entry) => {
        const quote = data.stocks?.quotes[entry.symbol];
        if (!quote) return [];
        const shares = data.metadata?.sharesOutstanding[entry.symbol] ?? 0;
        return [{ ...stockTile(entry.symbol, entry.name, quote, data, tf), group: entry.sector, size: quote.price * shares }];
      });
    case "crypto":
      return Object.keys(data.crypto?.coins ?? {})
        .sort((a, b) => data.crypto!.coins[b]!.marketCap - data.crypto!.coins[a]!.marketCap)
        .slice(0, TOP_COINS)
        .map((id) => ({ ...coinTile(id, data, live, tf, year)!, size: data.crypto!.coins[id]!.marketCap }));
    case "watchlist":
      return watchlist.map((item) => {
        if (item.type === "crypto") return coinTile(item.id, data, live, tf, year) ?? emptyTile(item.id, item.symbol);
        const quote = data.stocks?.quotes[item.symbol];
        const name = data.metadata?.sp500.find((e) => e.symbol === item.symbol)?.name ?? item.symbol;
        return quote ? stockTile(item.symbol, name, quote, data, tf) : emptyTile(item.symbol, item.symbol);
      });
  }
}

function stockTile(symbol: string, name: string, quote: StockQuote, data: MarketData, tf: Timeframe): Tile {
  const baseline = data.baselines?.stocks[symbol];
  return {
    id: symbol,
    label: symbol,
    name,
    group: null,
    size: 1,
    price: quote.price,
    change: computeChange(quote.price, stockBaselineFor(quote, baseline, tf)),
    volume: quote.volume,
    delayed: false,
    spark: [...priorCloses(quote, baseline), quote.price],
  };
}

function coinTile(id: string, data: MarketData, live: LivePrices, tf: Timeframe, year: number): Tile | null {
  const coin = data.crypto?.coins[id];
  if (!coin) return null;
  const livePrice = coin.coinbaseProductId ? live[coin.coinbaseProductId] : undefined;
  const price = livePrice ?? coin.price;
  return {
    id,
    label: coin.symbol,
    name: coin.name,
    group: null,
    size: 1,
    price,
    change: computeChange(price, coinBaselineFor(coin, data.baselines?.crypto[id], tf, year)),
    volume: null,
    delayed: coin.coinbaseProductId === null,
    spark: coin.sparkline7d,
  };
}

function emptyTile(id: string, label: string): Tile {
  return { id, label, name: label, group: null, size: 1, price: null, change: null, volume: null, delayed: false, spark: [] };
}
