import { describe, expect, it } from "vitest";
import { beaufort, fmtPrecip, fmtPressure, fmtTemp, fmtWind } from "./units";

describe("units", () => {
  it("formats temperatures", () => {
    expect(fmtTemp(21.4, "c")).toBe("21°");
    expect(fmtTemp(-0.3, "c")).toBe("0°");
    expect(fmtTemp(20, "f")).toBe("68°");
    expect(fmtTemp(null, "c")).toBe("–");
  });

  it("converts wind", () => {
    expect(fmtWind(36, "kmh")).toBe("36 km/h");
    expect(fmtWind(36, "ms")).toBe("10 m/s");
    expect(fmtWind(37.04, "kn")).toBe("20 kn");
    expect(beaufort(0)).toBe(0);
    expect(beaufort(30)).toBe(5);
    expect(beaufort(130)).toBe(12);
  });

  it("formats rain and pressure", () => {
    expect(fmtPrecip(2.4, "mm")).toBe("2.4 mm");
    expect(fmtPrecip(12, "mm")).toBe("12 mm");
    expect(fmtPrecip(25.4, "in")).toBe("1.0″");
    expect(fmtPressure(1013.2, "hpa")).toBe("1013 hPa");
    expect(fmtPressure(1013.25, "inhg")).toBe("29.92 inHg");
  });
});
