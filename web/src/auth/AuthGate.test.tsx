import type { AppConfig } from "@sh/shared";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { useEffect, useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useApi } from "../api/ApiProvider";
import { AuthGate } from "./AuthGate";

const auth = vi.hoisted(() => ({
  state: {} as Record<string, unknown>,
  signinRedirect: vi.fn(async () => {}),
}));

vi.mock("react-oidc-context", () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAuth: () => ({ signinRedirect: auth.signinRedirect, events: { addSilentRenewError: () => () => {} }, ...auth.state }),
}));

const REMOTE: AppConfig = { apiUrl: "https://api.example.com", auth: { authority: "https://issuer.example.com", clientId: "abc" } };

/** Calls the API once and shows which Authorization header was sent. */
function Probe() {
  const api = useApi();
  const [sent, setSent] = useState<string | null>(null);
  useEffect(() => {
    void api("/market-data").then(() => setSent(new Headers(vi.mocked(fetch).mock.calls[0]![1]!.headers).get("Authorization") ?? "none"));
  }, [api]);
  return <p>sent: {sent}</p>;
}

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 200 })));
  auth.signinRedirect.mockClear();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("AuthGate", () => {
  it("skips login when the config has no auth (local development)", async () => {
    render(<AuthGate config={{ apiUrl: "/api", auth: null }}><Probe /></AuthGate>);

    expect(await screen.findByText("sent: none")).toBeTruthy();
    expect(auth.signinRedirect).not.toHaveBeenCalled();
  });

  it("sends a signed-out visitor to the login page", async () => {
    auth.state = { isLoading: false, isAuthenticated: false, activeNavigator: undefined, user: null };

    render(<AuthGate config={REMOTE}><Probe /></AuthGate>);

    await waitFor(() => expect(auth.signinRedirect).toHaveBeenCalledOnce());
    expect(screen.queryByText(/sent:/)).toBeNull();
  });

  it("calls the API with the access token once signed in", async () => {
    auth.state = { isLoading: false, isAuthenticated: true, user: { access_token: "jwt-123" } };

    render(<AuthGate config={REMOTE}><Probe /></AuthGate>);

    expect(await screen.findByText("sent: Bearer jwt-123")).toBeTruthy();
  });
});
