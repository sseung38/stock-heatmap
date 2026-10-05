import { expect, it } from "vitest";
import { etDate, formatEtTime } from "../src/index";

// Same cases as the Java EtTimeTest.
it("converts UTC instants to New York dates", () => {
  expect(etDate("2026-01-02T04:30:00Z")).toBe("2026-01-01");
  expect(etDate("2026-07-01T03:59:00Z")).toBe("2026-06-30");
});

it("formats New York clock time", () => {
  expect(formatEtTime("2026-10-02T18:35:00Z")).toBe("14:35 ET");
  expect(formatEtTime("2026-01-05T14:30:00Z")).toBe("09:30 ET");
});
