import type { Timeframe } from "@sh/shared";
import { useEffect, useRef, useState } from "react";
import type { Tile } from "./buildTiles";
import { changeColor } from "./color";
import { formatChange } from "./format";
import { layoutTreemap } from "./layoutTreemap";

/** Fallback size when the element can't be measured (e.g. tests without layout). */
const DEFAULT_SIZE = { width: 1000, height: 600 };

function useElementSize<T extends Element>() {
  const ref = useRef<T>(null);
  const [size, setSize] = useState(DEFAULT_SIZE);
  useEffect(() => {
    const element = ref.current;
    if (!element || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry!.contentRect;
      if (width > 0 && height > 0) setSize({ width, height });
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return [ref, size] as const;
}

export function Heatmap({ tiles, grouped, tf, onSelect }: { tiles: Tile[]; grouped: boolean; tf: Timeframe; onSelect: (tile: Tile) => void }) {
  const [ref, { width, height }] = useElementSize<HTMLDivElement>();
  const byId = new Map(tiles.map((t) => [t.id, t]));
  const layout = layoutTreemap(tiles.map(({ id, group, size }) => ({ id, group, size })), width, height, grouped);

  return (
    <div className="heatmap" ref={ref}>
      <svg width={width} height={height}>
        {layout.groups.map((g) => (
          <text key={g.name} className="group-label" x={g.x + 4} y={g.y + 12}>
            {g.name.toUpperCase()}
          </text>
        ))}
        {layout.tiles.map((rect) => {
          const tile = byId.get(rect.id)!;
          const change = formatChange(tile.change);
          const fontSize = Math.max(9, Math.min(24, Math.min(rect.w, rect.h) / 4));
          return (
            <g
              key={rect.id}
              role="button"
              tabIndex={0}
              aria-label={`${tile.label} ${change}`}
              className="tile"
              onClick={() => onSelect(tile)}
              onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onSelect(tile)}
            >
              <rect x={rect.x} y={rect.y} width={rect.w} height={rect.h} fill={changeColor(tile.change, tf)} />
              {rect.w > 40 && rect.h > 24 && (
                <text x={rect.x + rect.w / 2} y={rect.y + rect.h / 2} textAnchor="middle" fontSize={fontSize}>
                  <tspan x={rect.x + rect.w / 2} dy="-0.2em" fontWeight={600}>{tile.label}</tspan>
                  <tspan x={rect.x + rect.w / 2} dy="1.2em" fontSize={fontSize * 0.75}>{change}</tspan>
                </text>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}
