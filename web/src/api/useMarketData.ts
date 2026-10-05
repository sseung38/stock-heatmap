import type { MarketData } from "@sh/shared";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useApi } from "./ApiProvider";

/** Polls GET /market-data every 60 s; keeps showing the last data while a refetch fails. */
export function useMarketData() {
  const api = useApi();
  const query = useQuery({
    queryKey: ["market-data"],
    queryFn: () => api<MarketData>("/market-data"),
    refetchInterval: 60_000,
    placeholderData: keepPreviousData,
    retry: false,
  });
  return { data: query.data, isError: query.isError };
}
