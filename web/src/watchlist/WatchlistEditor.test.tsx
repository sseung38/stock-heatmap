import type { CoinQuote, Watchlist } from "@sh/shared";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { ApiProvider } from "../api/ApiProvider";
import { WatchlistEditor } from "./WatchlistEditor";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const SAVED: Watchlist = { items: [{ type: "stock", symbol: "PLTR" }], updatedAt: "2026-10-05T12:00:00Z" };
const COINS = { solana: { symbol: "SOL", name: "Solana" } as CoinQuote };

/** fetch stub: GET returns SAVED; PUT answers with `put(body)`. */
function stubApi(put: (body: unknown) => Response) {
  const fetchMock = vi.fn(async (_url: string, init?: RequestInit) =>
    init?.method === "PUT" ? put(JSON.parse(String(init.body))) : new Response(JSON.stringify(SAVED), { status: 200 }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderEditor(onClose = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ApiProvider config={{ apiUrl: "/api", auth: null }} token={null}>
        <WatchlistEditor coins={COINS} onClose={onClose} />
      </ApiProvider>
    </QueryClientProvider>,
  );
  return onClose;
}

const row = (label: string) => screen.getByText(label).closest("li")!;

function add(type: "stock" | "crypto", value: string) {
  fireEvent.change(screen.getByLabelText("Type"), { target: { value: type } });
  fireEvent.change(screen.getByLabelText(type === "stock" ? "Ticker" : "CoinGecko id"), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "Add" }));
}

it("flags rejected items and keeps the list when the API returns 400", async () => {
  stubApi(() =>
    new Response(JSON.stringify({ error: "Unknown or duplicate items", invalid: [{ type: "stock", symbol: "ZZZZ" }] }), { status: 400 }),
  );
  const onClose = renderEditor();
  await screen.findByText("PLTR");

  add("stock", "zzzz");
  fireEvent.click(screen.getByRole("button", { name: "Save" }));

  expect(await screen.findByText("Unknown or duplicate items")).toBeTruthy();
  expect(row("ZZZZ").getAttribute("aria-invalid")).toBe("true");
  expect(row("PLTR").getAttribute("aria-invalid")).toBe("false");
  expect(onClose).not.toHaveBeenCalled();
});

it("saves a coin with its symbol and closes", async () => {
  const fetchMock = stubApi((body) => new Response(JSON.stringify({ ...(body as object), updatedAt: "now" }), { status: 200 }));
  const onClose = renderEditor();
  await screen.findByText("PLTR");

  add("crypto", "Solana");
  fireEvent.click(screen.getByRole("button", { name: "Save" }));

  await waitFor(() => expect(onClose).toHaveBeenCalled());
  const put = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT")!;
  expect(JSON.parse(String(put[1]!.body))).toEqual({
    items: [{ type: "stock", symbol: "PLTR" }, { type: "crypto", id: "solana", symbol: "SOL" }],
  });
});

it("removes an item before saving", async () => {
  const fetchMock = stubApi((body) => new Response(JSON.stringify(body), { status: 200 }));
  renderEditor();
  await screen.findByText("PLTR");

  fireEvent.click(screen.getByRole("button", { name: "Remove PLTR" }));
  fireEvent.click(screen.getByRole("button", { name: "Save" }));

  await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(true));
  const put = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT")!;
  expect(JSON.parse(String(put[1]!.body))).toEqual({ items: [] });
});
