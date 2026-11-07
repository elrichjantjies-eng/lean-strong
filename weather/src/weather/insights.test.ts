import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { parseForecast } from "@/data/openMeteo";
import { formatter } from "@/lib/units";
import { DEFAULT_SETTINGS } from "@/state/settings";
import {
  aqiCategory,
  headline,
  nowcast,
  pressureTrend,
  todayIndex,
  uvCategory,
  vsYesterday,
  whatToTake,
} from "./insights";
import { lifeIndices } from "./lifeIndex";
import { condition, conditionIcon } from "./codes";
import { ICONS } from "@/icons/defs";

const f = parseForecast(
  JSON.parse(readFileSync(new URL("../../e2e/fixtures/forecast.json", import.meta.url), "utf8")),
);
const now = 1794050400; // when the fixture was captured
const fmt = formatter(DEFAULT_SETTINGS);

describe("rain nowcast", () => {
  const slots = (mm: number[]) => mm.map((precip, i) => ({ time: now + i * 900, precip }));

  it("says nothing when it stays dry", () => {
    expect(nowcast(slots([0, 0, 0, 0, 0]), now, 7200, true)).toBeNull();
  });

  it("announces rain starting", () => {
    const n = nowcast(slots([0, 0, 0.4, 1, 1]), now, 7200, true);
    expect(n?.kind).toBe("starting");
    expect(n?.text).toBe("Rain starting in about 30 min");
  });

  it("announces rain stopping", () => {
    const n = nowcast(slots([1, 1, 0.5, 0, 0]), now, 7200, true);
    expect(n?.kind).toBe("stopping");
    expect(n?.text).toBe("Rain stopping in about 45 min");
  });
});

describe("insights", () => {
  it("finds today after yesterday", () => {
    expect(todayIndex(f, now)).toBe(1);
  });

  it("writes a headline and a comparison with yesterday", () => {
    expect(headline(f, now, fmt).length).toBeGreaterThan(10);
    expect(vsYesterday(f, now, fmt)).toMatch(/yesterday/);
  });

  it("categorises AQI and UV", () => {
    expect(aqiCategory(42, "us").label).toBe("Good");
    expect(aqiCategory(120, "us").label).toBe("Unhealthy for sensitive groups");
    expect(aqiCategory(70, "eu").label).toBe("Poor");
    expect(uvCategory(2).label).toBe("Low");
    expect(uvCategory(9).label).toBe("Very high");
    expect(uvCategory(12).label).toBe("Extreme");
  });

  it("works out the pressure trend", () => {
    expect(["rising", "falling", "steady"]).toContain(pressureTrend(f, now).trend);
  });

  it("suggests what to take", () => {
    expect(Array.isArray(whatToTake(f, now))).toBe(true);
  });

  it("scores the activities between 0 and 10", () => {
    const list = lifeIndices(f, now, 40);
    expect(list.map((a) => a.id)).toEqual([
      "run",
      "braai",
      "laundry",
      "beach",
      "stars",
      "carwash",
      "drive",
      "garden",
    ]);
    for (const a of list) {
      expect(a.score).toBeGreaterThanOrEqual(0);
      expect(a.score).toBeLessThanOrEqual(10);
      expect(a.reason).toBeTruthy();
    }
  });
});

describe("weather codes", () => {
  it("has an icon for every code, day and night", () => {
    for (const code of [
      0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85,
      86, 95, 96, 99,
    ]) {
      expect(ICONS[conditionIcon(code, true)]).toBeDefined();
      expect(ICONS[conditionIcon(code, false)]).toBeDefined();
    }
    expect(condition(1234).label).toBe("Cloudy");
  });
});
