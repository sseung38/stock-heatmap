import { expect, it } from "vitest";
import { backoffDelay, parseTicker } from "./ticker";

it("parses a Coinbase ticker message", () => {
  const raw = JSON.stringify({ type: "ticker", sequence: 1, product_id: "BTC-USD", price: "98765.43", time: "2026-10-04T12:00:00Z" });

  expect(parseTicker(raw)).toEqual({ productId: "BTC-USD", price: 98765.43 });
});

it.each([
  ['{"type":"subscriptions","channels":[]}'],
  ['{"type":"error","message":"Failed to subscribe"}'],
  ['{"type":"ticker","product_id":"BTC-USD","price":"abc"}'],
  ['{"type":"ticker","price":"1"}'],
  ["not json"],
])("ignores %s", (raw) => {
  expect(parseTicker(raw)).toBeNull();
});

it("backs off exponentially up to 30 seconds", () => {
  expect(backoffDelay(0)).toBe(1000);
  expect(backoffDelay(1)).toBe(2000);
  expect(backoffDelay(4)).toBe(16000);
  expect(backoffDelay(5)).toBe(30000);
  expect(backoffDelay(12)).toBe(30000);
});
