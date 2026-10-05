import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useCryptoStream } from "./useCryptoStream";

/** Minimal stand-in for the browser WebSocket that the test drives by hand. */
class FakeSocket {
  static instances: FakeSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  sent: string[] = [];
  closed = false;
  constructor(readonly url: string) {
    FakeSocket.instances.push(this);
  }
  send(data: string) {
    this.sent.push(data);
  }
  close() {
    this.closed = true;
  }
  open() {
    this.onopen?.();
  }
  tick(productId: string, price: string) {
    this.onmessage?.({ data: JSON.stringify({ type: "ticker", product_id: productId, price }) });
  }
}

const Impl = FakeSocket as unknown as typeof WebSocket;

beforeEach(() => {
  FakeSocket.instances = [];
  vi.useFakeTimers();
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

it("subscribes to the ticker channel for the given products", () => {
  renderHook(() => useCryptoStream(["BTC-USD", "ETH-USD"], { WebSocketImpl: Impl }));
  const socket = FakeSocket.instances[0]!;
  act(() => socket.open());

  expect(socket.url).toBe("wss://ws-feed.exchange.coinbase.com");
  expect(JSON.parse(socket.sent[0]!)).toEqual({ type: "subscribe", product_ids: ["BTC-USD", "ETH-USD"], channels: ["ticker"] });
});

it("batches ticks and publishes the latest price once per flush", () => {
  const { result } = renderHook(() => useCryptoStream(["BTC-USD"], { WebSocketImpl: Impl }));
  const socket = FakeSocket.instances[0]!;
  act(() => socket.open());
  expect(result.current.status).toBe("live");

  act(() => {
    socket.tick("BTC-USD", "100");
    socket.tick("BTC-USD", "101");
  });
  expect(result.current.prices).toEqual({}); // not yet flushed

  act(() => vi.advanceTimersByTime(1000));
  expect(result.current.prices).toEqual({ "BTC-USD": 101 });
});

it("reconnects with backoff after the connection drops, keeping last prices", () => {
  const { result } = renderHook(() => useCryptoStream(["BTC-USD"], { WebSocketImpl: Impl }));
  act(() => {
    FakeSocket.instances[0]!.open();
    FakeSocket.instances[0]!.tick("BTC-USD", "100");
    vi.advanceTimersByTime(1000);
  });

  act(() => FakeSocket.instances[0]!.onclose?.());
  expect(result.current.status).toBe("reconnecting");
  expect(result.current.prices).toEqual({ "BTC-USD": 100 });
  expect(FakeSocket.instances).toHaveLength(1);

  act(() => vi.advanceTimersByTime(1000));
  expect(FakeSocket.instances).toHaveLength(2);
});

it("does not connect when there is nothing to stream", () => {
  renderHook(() => useCryptoStream([], { WebSocketImpl: Impl }));

  expect(FakeSocket.instances).toHaveLength(0);
});
