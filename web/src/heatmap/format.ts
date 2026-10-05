/** "+1.24%", "-0.30%", or "n/a" when the change is unknown. */
export function formatChange(change: number | null): string {
  if (change === null) return "n/a";
  const pct = (change * 100).toFixed(2);
  return change >= 0 ? `+${pct}%` : `${pct}%`;
}

export function formatPrice(price: number | null): string {
  if (price === null) return "n/a";
  const digits = price >= 1 ? 2 : 6;
  return price.toLocaleString("en-US", { style: "currency", currency: "USD", maximumFractionDigits: digits });
}

export function formatVolume(volume: number): string {
  return volume.toLocaleString("en-US");
}
