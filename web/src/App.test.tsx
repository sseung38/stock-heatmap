import type { MarketData } from "@sh/shared";
import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { renderApp } from "./test/renderApp";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const data: MarketData = {
  stocks: {
    asOf: new Date().toISOString(),
    market: { isOpen: true },
    quotes: { AAPL: { price: 110, prevClose: 100, volume: 51234567, tradingDay: "2026-10-02", asOf: "" } },
  },
  crypto: null,
  baselines: {
    asOf: "",
    stocks: { AAPL: { closes: Array.from({ length: 30 }, () => 120), lastCloseDate: "2026-10-01", yearEndCloses: {} } },
    crypto: {},
  },
  metadata: {
    asOf: "",
    sp500: [{ symbol: "AAPL", name: "Apple Inc.", sector: "Information Technology" }],
    sharesOutstanding: { AAPL: 1e9 },
  },
};

const aaplFill = () => screen.getByRole("button", { name: /^AAPL / }).querySelector("rect")!.getAttribute("fill");

it("opens the detail panel when a tile is clicked", async () => {
  renderApp(data);

  fireEvent.click(await screen.findByRole("button", { name: "AAPL +10.00%" }));

  expect(screen.getByText("Apple Inc.")).toBeTruthy();
  expect(screen.getByText("51,234,567")).toBeTruthy();
});

it("recolors tiles when the timeframe changes", async () => {
  renderApp(data);
  await screen.findByRole("button", { name: "AAPL +10.00%" });
  const oneDay = aaplFill();

  fireEvent.click(screen.getByRole("button", { name: "1W" }));

  expect(screen.getByRole("button", { name: "AAPL -8.33%" })).toBeTruthy(); // 110 vs 120
  expect(aaplFill()).not.toBe(oneDay);
});

it("shows the status line and CoinGecko attribution", async () => {
  renderApp(data);

  expect(await screen.findByText(/^Stocks as of \d\d:\d\d ET/)).toBeTruthy();
  expect(screen.getByText("Crypto data by CoinGecko")).toBeTruthy();
});

it("explains the empty state before the first refresh", async () => {
  renderApp({ stocks: null, crypto: null, baselines: null, metadata: null });

  expect(await screen.findByText("Waiting for first data refresh")).toBeTruthy();
});
