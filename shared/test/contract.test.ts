import { expect, it } from "vitest";
import type { BaselinesFile, CryptoFile, MetadataFile, StocksFile, Watchlist } from "../src/index";
import baselines from "../../contract/baselines.json";
import crypto from "../../contract/crypto.json";
import metadata from "../../contract/metadata.json";
import stocks from "../../contract/stocks.json";
import watchlist from "../../contract/watchlist.json";

// The Java ContractTest round-trips these same files. The assignments below are checked by
// `npm run typecheck`: renaming a field on either side breaks one of the two builds.
const s: StocksFile = stocks;
const c: CryptoFile = crypto;
const b: BaselinesFile = baselines;
const m: MetadataFile = metadata;
const w = watchlist as Watchlist;

it("contract samples match the TypeScript types", () => {
  expect(s.market.isOpen).toBeTypeOf("boolean");
  expect(s.quotes["BRK.B"]?.tradingDay).toBe("2026-10-02");
  expect(c.coins["binancecoin"]?.coinbaseProductId).toBeNull();
  expect(b.stocks["AAPL"]?.yearEndCloses["2025"]).toBe(250.42);
  expect(m.sp500).toHaveLength(2);
  expect(w.items.map((i) => i.type)).toEqual(["stock", "crypto"]);
});
