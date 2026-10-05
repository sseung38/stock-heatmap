import { expect, it } from "vitest";
import { labelFits } from "./Heatmap";

it("fits short labels in wide tiles", () => {
  expect(labelFits("AAPL", 20, 200)).toBe(true);
});

it("hides labels wider than their tile", () => {
  expect(labelFits("FIGR_HELOC", 14, 80)).toBe(false);
});
