import { describe, expect, it } from "vitest";
import { moonInfo, nextPhase, moonTimes } from "./astro";

const t = (iso: string) => Date.parse(iso) / 1000;

describe("moon", () => {
  it("is full on 13 January 2025 and new on 29 January 2025", () => {
    const full = moonInfo(t("2025-01-13T22:27:00Z"));
    expect(full.name).toBe("Full Moon");
    expect(full.fraction).toBeGreaterThan(0.98);
    const nu = moonInfo(t("2025-01-29T12:36:00Z"));
    expect(nu.name).toBe("New Moon");
    expect(nu.fraction).toBeLessThan(0.02);
  });

  it("finds the next full moon within a few hours", () => {
    const next = nextPhase(t("2025-01-01T00:00:00Z"), 0.5);
    expect(Math.abs(next - t("2025-01-13T22:27:00Z"))).toBeLessThan(6 * 3600);
  });

  it("finds a moonrise or moonset during the day", () => {
    const { rise, set } = moonTimes(t("2025-01-13T22:00:00Z"), -33.92, 18.42);
    expect(rise != null || set != null).toBe(true);
  });
});
