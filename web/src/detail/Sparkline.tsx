import { COLORS } from "../heatmap/color";

/** A small line chart of the values, oldest first. */
export function Sparkline({ values, width = 260, height = 64 }: { values: number[]; width?: number; height?: number }) {
  if (values.length < 2) return null;
  const min = Math.min(...values);
  const range = Math.max(...values) - min || 1;
  const points = values
    .map((v, i) => `${(i / (values.length - 1)) * width},${height - ((v - min) / range) * height}`)
    .join(" ");
  const up = values[values.length - 1]! >= values[0]!;
  return (
    <svg className="sparkline" width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-label="Price history">
      <polyline points={points} fill="none" stroke={up ? COLORS.green : COLORS.red} strokeWidth={2} />
    </svg>
  );
}
