import type { AppConfig } from "@sh/shared";
import { afterEach, expect, it, vi } from "vitest";
import { ApiError, apiFetch } from "./client";

const config: AppConfig = { apiUrl: "https://api.example.com", auth: null };

afterEach(() => vi.unstubAllGlobals());

it("sends the bearer token and parses JSON", async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response('{"ok":1}', { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);

  await expect(apiFetch(config, "/market-data", "tok")).resolves.toEqual({ ok: 1 });

  const [url, init] = fetchMock.mock.calls[0]!;
  expect(url).toBe("https://api.example.com/market-data");
  expect(new Headers(init.headers).get("Authorization")).toBe("Bearer tok");
});

it("omits the header without a token", async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);

  await apiFetch(config, "/market-data", null);

  expect(new Headers(fetchMock.mock.calls[0]![1].headers).has("Authorization")).toBe(false);
});

it("raises ApiError with the parsed body", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response('{"error":"bad"}', { status: 400 })));

  const error = await apiFetch(config, "/watchlist", "tok").catch((e: unknown) => e);

  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(400);
  expect((error as ApiError).body).toEqual({ error: "bad" });
});
