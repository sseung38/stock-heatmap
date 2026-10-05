import type { AppConfig } from "@sh/shared";
import { createContext, useCallback, useContext, type ReactNode } from "react";
import { apiFetch } from "./client";

type Api = <T>(path: string, init?: RequestInit) => Promise<T>;

const ApiContext = createContext<Api | null>(null);

/** Gives components an `api(path)` function bound to the config and the current access token. */
export function ApiProvider({ config, token, children }: { config: AppConfig; token: string | null; children: ReactNode }) {
  const api = useCallback(<T,>(path: string, init?: RequestInit) => apiFetch<T>(config, path, token, init), [config, token]);
  return <ApiContext.Provider value={api}>{children}</ApiContext.Provider>;
}

export function useApi(): Api {
  const api = useContext(ApiContext);
  if (!api) throw new Error("useApi must be used inside <ApiProvider>");
  return api;
}
