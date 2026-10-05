import { formatChange, formatPrice, formatVolume } from "../heatmap/format";
import type { Tile } from "../heatmap/buildTiles";
import { Sparkline } from "./Sparkline";

export function TileDetail({ tile, onClose }: { tile: Tile; onClose: () => void }) {
  return (
    <aside className="tile-detail" aria-label={`${tile.label} details`}>
      <button className="close" onClick={onClose} aria-label="Close">×</button>
      <h2>{tile.name}</h2>
      <p className="symbol">
        {tile.label} {tile.delayed && <span className="badge">delayed</span>}
      </p>
      <p className="price">{formatPrice(tile.price)}</p>
      <p className={tile.change !== null && tile.change < 0 ? "down" : "up"}>{formatChange(tile.change)}</p>
      {tile.volume !== null && (
        <p className="volume">
          Volume <span>{formatVolume(tile.volume)}</span>
        </p>
      )}
      <Sparkline values={tile.spark} />
    </aside>
  );
}
