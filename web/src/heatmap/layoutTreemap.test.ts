import { describe, expect, it } from "vitest";
import { layoutTreemap, type Rect, type TreemapItem } from "./layoutTreemap";

const W = 800;
const H = 500;
const area = (r: Rect) => r.w * r.h;
const overlaps = (a: Rect, b: Rect) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

describe("layoutTreemap", () => {
  const items: TreemapItem[] = [
    { id: "AAPL", group: "Tech", size: 300 },
    { id: "MSFT", group: "Tech", size: 280 },
    { id: "JPM", group: "Financials", size: 60 },
    { id: "XOM", group: "Energy", size: 45 },
  ];

  it("keeps tiles inside the canvas without overlapping", () => {
    const { tiles } = layoutTreemap(items, W, H, false);

    expect(tiles).toHaveLength(4);
    for (const t of tiles) {
      expect(t.x).toBeGreaterThanOrEqual(0);
      expect(t.y).toBeGreaterThanOrEqual(0);
      expect(t.x + t.w).toBeLessThanOrEqual(W + 1e-6);
      expect(t.y + t.h).toBeLessThanOrEqual(H + 1e-6);
    }
    for (let i = 0; i < tiles.length; i++)
      for (let j = i + 1; j < tiles.length; j++) expect(overlaps(tiles[i]!, tiles[j]!)).toBe(false);
  });

  it("gives equal sizes equal areas", () => {
    const { tiles } = layoutTreemap(["A", "B", "C"].map((id) => ({ id, group: null, size: 1 })), W, H, false);
    const areas = tiles.map(area);

    expect(Math.max(...areas) / Math.min(...areas)).toBeLessThan(1.01);
  });

  it("still shows items with a missing or zero size", () => {
    const { tiles } = layoutTreemap(
      [{ id: "A", group: null, size: 100 }, { id: "ZERO", group: null, size: 0 }, { id: "NAN", group: null, size: Number.NaN }],
      W, H, false,
    );

    expect(tiles.find((t) => t.id === "ZERO")!.w * tiles.find((t) => t.id === "ZERO")!.h).toBeGreaterThan(0);
    expect(area(tiles.find((t) => t.id === "NAN")!)).toBeGreaterThan(0);
  });

  it("returns one rectangle per group with its tiles inside", () => {
    const { tiles, groups } = layoutTreemap(items, W, H, true);

    expect(groups.map((g) => g.name).sort()).toEqual(["Energy", "Financials", "Tech"]);
    for (const item of items) {
      const tile = tiles.find((t) => t.id === item.id)!;
      const group = groups.find((g) => g.name === item.group)!;
      expect(tile.x).toBeGreaterThanOrEqual(group.x);
      expect(tile.y).toBeGreaterThanOrEqual(group.y + 16 - 1e-6); // below the group label
      expect(tile.x + tile.w).toBeLessThanOrEqual(group.x + group.w + 1e-6);
      expect(tile.y + tile.h).toBeLessThanOrEqual(group.y + group.h + 1e-6);
    }
  });

  it("handles no items", () => {
    expect(layoutTreemap([], W, H, true)).toEqual({ tiles: [], groups: [] });
  });
});
