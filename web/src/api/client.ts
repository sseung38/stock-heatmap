import type { AppConfig } from "@sh/shared";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`API request failed with HTTP ${status}`);
  }
}

/** Calls the backend; adds the Cognito access token when signed in. */
export async function apiFetch<T>(config: AppConfig, path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body !== undefined) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${config.apiUrl}${path}`, { ...init, headers });
  const text = await response.text();
  const body: unknown = text ? JSON.parse(text) : null;
  if (!response.ok) throw new ApiError(response.status, body);
  return body as T;
}
