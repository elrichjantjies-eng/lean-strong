import { describe, expect, it } from "vitest";
import { bearing, compass, distanceKm, distanceToPolygonKm, pointInPolygon } from "./geo";

const square: [number, number][] = [
  [-33.8, 18.3],
  [-33.8, 18.6],
  [-34.1, 18.6],
  [-34.1, 18.3],
];

describe("geo", () => {
  it("measures distances", () => {
    // Cape Town → Johannesburg is about 1,260 km as the crow flies.
    expect(distanceKm(-33.92, 18.42, -26.2, 28.04)).toBeGreaterThan(1240);
    expect(distanceKm(-33.92, 18.42, -26.2, 28.04)).toBeLessThan(1280);
  });

  it("names compass points", () => {
    expect(compass(0)).toBe("N");
    expect(compass(359)).toBe("N");
    expect(compass(135)).toBe("SE");
    expect(compass(157.5, 16)).toBe("SSE");
    expect(Math.round(bearing(0, 0, 1, 0))).toBe(0);
    expect(Math.round(bearing(0, 0, 0, 1))).toBe(90);
  });

  it("knows when a point is inside a warning area", () => {
    expect(pointInPolygon(-33.92, 18.42, square)).toBe(true);
    expect(pointInPolygon(-26.2, 28.04, square)).toBe(false);
  });

  it("measures the distance to a storm cell", () => {
    expect(distanceToPolygonKm(-33.92, 18.42, square)).toBe(0);
    const d = distanceToPolygonKm(-33.92, 18.8, square); // ~0.2° east of the square
    expect(d).toBeGreaterThan(15);
    expect(d).toBeLessThan(22);
  });
});
