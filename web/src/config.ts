import type { AppConfig } from "@sh/shared";

/** Written by CDK next to the deployed site, or served by the local mock API in development. */
export async function loadConfig(): Promise<AppConfig> {
  const response = await fetch("/config.json", { cache: "no-store" });
  if (!response.ok) throw new Error(`Could not load /config.json (HTTP ${response.status})`);
  return (await response.json()) as AppConfig;
}
