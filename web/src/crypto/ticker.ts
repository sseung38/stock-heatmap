/** A price update from Coinbase's public "ticker" channel, or null for any other message. */
export function parseTicker(raw: string): { productId: string; price: number } | null {
  let message: unknown;
  try {
    message = JSON.parse(raw);
  } catch {
    return null;
  }
  if (typeof message !== "object" || message === null) return null;
  const { type, product_id, price } = message as Record<string, unknown>;
  const value = Number(price);
  if (type !== "ticker" || typeof product_id !== "string" || typeof price !== "string" || !Number.isFinite(value)) return null;
  return { productId: product_id, price: value };
}

/** 1 s, 2 s, 4 s, ... capped at 30 s. */
export function backoffDelay(attempt: number): number {
  return Math.min(1000 * 2 ** attempt, 30_000);
}
