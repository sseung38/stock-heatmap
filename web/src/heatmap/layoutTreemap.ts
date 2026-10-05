import { hierarchy, treemap, treemapSquarify, type HierarchyRectangularNode } from "d3-hierarchy";

export interface TreemapItem {
  id: string;
  /** Sector name when grouped, otherwise null. */
  group: string | null;
  size: number;
}

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** Height reserved at the top of each group for its label. */
export const GROUP_LABEL_HEIGHT = 16;

type Node = { name: string; item?: TreemapItem; children?: Node[] };

const toRect = (n: HierarchyRectangularNode<Node>): Rect => ({ x: n.x0, y: n.y0, w: n.x1 - n.x0, h: n.y1 - n.y0 });

/**
 * Squarified treemap. Items with a missing or non-positive size get the smallest valid size
 * present, so a tile with unknown market cap is still visible rather than vanishing.
 */
export function layoutTreemap(
  items: TreemapItem[],
  width: number,
  height: number,
  grouped: boolean,
): { tiles: (Rect & { id: string })[]; groups: (Rect & { name: string })[] } {
  if (items.length === 0) return { tiles: [], groups: [] };

  const valid = items.map((i) => i.size).filter((s) => Number.isFinite(s) && s > 0);
  const fallback = valid.length > 0 ? Math.min(...valid) : 1;
  const sizeOf = (item: TreemapItem) => (Number.isFinite(item.size) && item.size > 0 ? item.size : fallback);

  let children: Node[];
  if (grouped) {
    const byGroup = new Map<string, Node[]>();
    for (const item of items) {
      const name = item.group ?? "";
      byGroup.set(name, [...(byGroup.get(name) ?? []), { name: item.id, item }]);
    }
    children = [...byGroup].map(([name, leaves]) => ({ name, children: leaves }));
  } else {
    children = items.map((item) => ({ name: item.id, item }));
  }

  const root = hierarchy<Node>({ name: "root", children })
    .sum((d) => (d.item ? sizeOf(d.item) : 0))
    .sort((a, b) => (b.value ?? 0) - (a.value ?? 0));

  const laidOut = treemap<Node>()
    .size([width, height])
    .tile(treemapSquarify)
    .paddingInner(1)
    .paddingTop((node) => (grouped && node.depth === 1 ? GROUP_LABEL_HEIGHT : 0))(root);

  return {
    tiles: laidOut.leaves().map((leaf) => ({ id: leaf.data.item!.id, ...toRect(leaf) })),
    groups: grouped ? (laidOut.children ?? []).map((g) => ({ name: g.data.name, ...toRect(g) })) : [],
  };
}
