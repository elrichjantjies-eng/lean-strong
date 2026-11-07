import { describe, expect, it } from "vitest";
import { frameTimes, inSatelliteView, isoTime, parseTimeDimension } from "./satellite";

describe("satellite layers", () => {
  it("reads the latest image time from WMS capabilities", () => {
    const xml = `<Layer><Name>h60b</Name><Dimension name="time" default="2026-11-07T15:00:00Z" units="ISO8601" nearestValue="1">2022-12-09T00:45:00.000Z/2026-11-07T15:00:00.000Z/PT15M</Dimension></Layer>`;
    expect(parseTimeDimension(xml)).toEqual({
      end: Date.parse("2026-11-07T15:00:00Z") / 1000,
      step: 900,
    });
    expect(parseTimeDimension("<Layer/>")).toBeNull();
  });

  it("lists the last frames, oldest first", () => {
    expect(frameTimes(3600, 900, 3)).toEqual([1800, 2700, 3600]);
    expect(isoTime(Date.parse("2026-11-07T15:00:00Z") / 1000)).toBe("2026-11-07T15:00:00Z");
  });

  it("covers Africa and Europe but not the Americas or Australia", () => {
    expect(inSatelliteView(-33.9, 18.4)).toBe(true);
    expect(inSatelliteView(51.5, -0.1)).toBe(true);
    expect(inSatelliteView(40.7, -74)).toBe(false);
    expect(inSatelliteView(-33.9, 151.2)).toBe(false);
  });
});
