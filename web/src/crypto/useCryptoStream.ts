import { useEffect, useState } from "react";
import { backoffDelay, parseTicker } from "./ticker";

export const COINBASE_FEED = "wss://ws-feed.exchange.coinbase.com";

export type StreamStatus = "connecting" | "live" | "reconnecting";

/**
 * Live prices from Coinbase's public WebSocket (no API key). Ticks are buffered and published at
 * most once per flush so the heatmap re-renders about once a second, not on every trade. While
 * reconnecting, the last streamed prices are kept.
 */
export function useCryptoStream(
  productIds: string[],
  { WebSocketImpl = WebSocket, flushMs = 1000 }: { WebSocketImpl?: typeof WebSocket; flushMs?: number } = {},
): { prices: Record<string, number>; status: StreamStatus } {
  const [prices, setPrices] = useState<Record<string, number>>({});
  const [status, setStatus] = useState<StreamStatus>("connecting");
  const key = productIds.join(",");

  useEffect(() => {
    const ids = key ? key.split(",") : [];
    if (ids.length === 0) return;
    const wanted = new Set(ids);
    let buffer: Record<string, number> = {};
    let socket: WebSocket | null = null;
    let attempt = 0;
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
    let disposed = false;

    const flushTimer = setInterval(() => {
      if (Object.keys(buffer).length === 0) return;
      const batch = buffer;
      buffer = {};
      setPrices((previous) => ({ ...previous, ...batch }));
    }, flushMs);

    const connect = () => {
      socket = new WebSocketImpl(COINBASE_FEED);
      socket.onopen = () => {
        attempt = 0;
        setStatus("live");
        socket?.send(JSON.stringify({ type: "subscribe", product_ids: ids, channels: ["ticker"] }));
      };
      socket.onmessage = (event: MessageEvent) => {
        const tick = parseTicker(String(event.data));
        if (tick && wanted.has(tick.productId)) buffer[tick.productId] = tick.price;
      };
      socket.onclose = () => {
        if (disposed) return;
        setStatus("reconnecting");
        reconnectTimer = setTimeout(connect, backoffDelay(attempt++));
      };
    };
    connect();

    return () => {
      disposed = true;
      clearInterval(flushTimer);
      clearTimeout(reconnectTimer);
      if (socket) {
        socket.onclose = null;
        socket.close();
      }
    };
  }, [key, WebSocketImpl, flushMs]);

  return { prices, status };
}
