import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { forecastUrl, parseForecast } from "./openMeteo";

const raw = JSON.parse(
  readFileSync(new URL("../../e2e/fixtures/forecast.json", import.meta.url), "utf8"),
);

describe("Open-Meteo forecast", () => {
  it("asks for everything the app shows, in Unix time", () => {
    const url = new URL(forecastUrl(-33.9249, 18.4241));
    expect(url.searchParams.get("timeformat")).toBe("unixtime");
    expect(url.searchParams.get("past_days")).toBe("1");
    expect(url.searchParams.get("hourly")).toContain("uv_index");
    expect(url.searchParams.get("daily")).toContain("sunrise");
  });

  it("normalises the response", () => {
    const f = parseForecast(raw, 123);
    expect(f.fetchedAt).toBe(123);
    expect(f.timezone).toBe("Africa/Johannesburg");
    expect(f.utcOffset).toBe(7200);
    expect(f.daily).toHaveLength(17); // yesterday + 16 days
    expect(f.hourly.length).toBe(17 * 24);
    expect(f.current.temp).toBeTypeOf("number");
    for (const d of f.daily) expect(d.max).toBeGreaterThanOrEqual(d.min);
    expect(f.minutely).toHaveLength(12);
  });
});
