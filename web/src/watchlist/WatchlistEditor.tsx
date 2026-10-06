import type { CoinQuote, WatchlistItem } from "@sh/shared";
import { useState, type FormEvent } from "react";
import { itemKey, useWatchlist } from "./useWatchlist";

type Props = { coins: Record<string, CoinQuote>; onClose: () => void };

/** Add/remove stocks and coins, then save the whole list in one PUT. */
export function WatchlistEditor({ coins, onClose }: Props) {
  const { items, isLoading, save, invalid, error } = useWatchlist();
  const [edited, setEdited] = useState<WatchlistItem[] | null>(null);
  const [type, setType] = useState<"stock" | "crypto">("stock");
  const [input, setInput] = useState("");
  const [saving, setSaving] = useState(false);

  const draft = edited ?? items;
  const invalidKeys = new Set(invalid.map(itemKey));

  const add = (event: FormEvent) => {
    event.preventDefault();
    const value = input.trim();
    if (!value) return;
    const item: WatchlistItem =
      type === "stock"
        ? { type: "stock", symbol: value.toUpperCase() }
        : { type: "crypto", id: value.toLowerCase(), symbol: coins[value.toLowerCase()]?.symbol ?? value.toUpperCase() };
    if (!draft.some((d) => itemKey(d) === itemKey(item))) setEdited([...draft, item]);
    setInput("");
  };

  const onSave = async () => {
    setSaving(true);
    try {
      await save(draft);
      onClose();
    } catch {
      // useWatchlist shows the error and marks rejected rows; keep the editor open.
    } finally {
      setSaving(false);
    }
  };

  return (
    <aside className="watchlist-editor" role="dialog" aria-label="Edit watchlist">
      <header>
        <h2>Watchlist</h2>
        <button className="close" onClick={onClose} aria-label="Close">×</button>
      </header>
      {isLoading ? (
        <p>Loading…</p>
      ) : (
        <ul>
          {draft.length === 0 && <li className="hint">Nothing yet. Add a ticker or a coin below.</li>}
          {draft.map((item) => (
            <li key={itemKey(item)} aria-invalid={invalidKeys.has(itemKey(item))}>
              <span>{item.symbol}</span>
              {item.type === "crypto" && <small>{item.id}</small>}
              <button aria-label={`Remove ${item.symbol}`} onClick={() => setEdited(draft.filter((d) => itemKey(d) !== itemKey(item)))}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <form className="add-row" onSubmit={add}>
        <select aria-label="Type" value={type} onChange={(e) => setType(e.target.value as "stock" | "crypto")}>
          <option value="stock">Stock</option>
          <option value="crypto">Crypto</option>
        </select>
        <input
          aria-label={type === "stock" ? "Ticker" : "CoinGecko id"}
          placeholder={type === "stock" ? "e.g. PLTR" : "e.g. solana"}
          list={type === "crypto" ? "coin-ids" : undefined}
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit">Add</button>
        <datalist id="coin-ids">
          {Object.entries(coins).map(([id, coin]) => (
            <option key={id} value={id}>{`${id} — ${coin.symbol}`}</option>
          ))}
        </datalist>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      <button className="save" onClick={onSave} disabled={saving || isLoading}>
        {saving ? "Saving…" : "Save"}
      </button>
    </aside>
  );
}
