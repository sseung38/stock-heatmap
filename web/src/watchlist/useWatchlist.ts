import type { Watchlist, WatchlistItem } from "@sh/shared";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { ApiError } from "../api/client";
import { useApi } from "../api/ApiProvider";

type ErrorBody = { error?: string; invalid?: WatchlistItem[] };

/**
 * The signed-in user's watchlist (GET/PUT /watchlist). `save` resolves once the server accepted
 * the list; on a 400 it records which items were rejected, keeps the saved list, and rejects.
 */
export function useWatchlist() {
  const api = useApi();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ["watchlist"], queryFn: () => api<Watchlist>("/watchlist") });
  const [invalid, setInvalid] = useState<WatchlistItem[]>([]);
  const [error, setError] = useState<string | null>(null);

  const save = useCallback(
    async (items: WatchlistItem[]) => {
      try {
        const saved = await api<Watchlist>("/watchlist", { method: "PUT", body: JSON.stringify({ items }) });
        queryClient.setQueryData(["watchlist"], saved);
        setInvalid([]);
        setError(null);
      } catch (e) {
        const body = e instanceof ApiError && e.status === 400 ? (e.body as ErrorBody) : null;
        setInvalid(body?.invalid ?? []);
        setError(body?.error ?? "Could not save the watchlist. Try again.");
        throw e;
      }
    },
    [api, queryClient],
  );

  return { items: query.data?.items ?? [], isLoading: query.isLoading, save, invalid, error };
}

/** Same identity rule as the server: stocks by ticker, coins by CoinGecko id. */
export const itemKey = (item: WatchlistItem) => (item.type === "stock" ? `stock:${item.symbol}` : `crypto:${item.id}`);
