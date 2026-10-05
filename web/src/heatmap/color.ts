import type { Timeframe } from "@sh/shared";

export const COLORS = { red: "#e5484d", gray: "#3b3f4a", green: "#30a46c", none: "#2a2d35" } as const;

/** Change at which a tile reaches full red/green, per timeframe. */
export const CLAMP: Record<Timeframe, number> = { "1D": 0.03, "1W": 0.06, "1M": 0.1, YTD: 0.2 };

function rgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function mix(from: string, to: string, t: number): string {
  const a = rgb(from);
  const b = rgb(to);
  return `#${a.map((v, i) => Math.round(v + (b[i]! - v) * t).toString(16).padStart(2, "0")).join("")}`;
}

/** Red below zero, green above, gray at zero; full color at ±CLAMP[tf]. */
export function changeColor(change: number | null, tf: Timeframe): string {
  if (change === null) return COLORS.none;
  const t = Math.max(-1, Math.min(1, change / CLAMP[tf]));
  return t < 0 ? mix(COLORS.gray, COLORS.red, -t) : mix(COLORS.gray, COLORS.green, t);
}
