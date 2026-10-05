import type { MarketData } from "@sh/shared";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { vi } from "vitest";
import App from "../App";
import { ApiProvider } from "../api/ApiProvider";

/** Renders the whole app with fetch answering /api/market-data from `data`. */
export function renderApp(data: MarketData) {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(data), { status: 200 })));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ApiProvider config={{ apiUrl: "/api", auth: null }} token={null}>
        <App />
      </ApiProvider>
    </QueryClientProvider>,
  );
}
