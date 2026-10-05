import { expect, it } from "vitest";
import { changeColor } from "./color";

it("hits the end colors at each timeframe's clamp", () => {
  expect(changeColor(-0.03, "1D")).toBe("#e5484d");
  expect(changeColor(0.05, "1D")).toBe("#30a46c");
  expect(changeColor(-0.2, "YTD")).toBe("#e5484d");
});

it("is neutral gray at zero and dark for missing data", () => {
  expect(changeColor(0, "YTD")).toBe("#3b3f4a");
  expect(changeColor(null, "1W")).toBe("#2a2d35");
});

it("interpolates inside the clamp", () => {
  const mid = changeColor(0.05, "YTD"); // a quarter of the way to green
  expect(mid).not.toBe("#30a46c");
  expect(mid).not.toBe("#3b3f4a");
});
