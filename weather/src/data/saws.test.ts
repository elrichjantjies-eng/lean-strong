import { describe, expect, it } from "vitest";
import { inSouthAfrica, nearestStation, STATIONS } from "./saws";

describe("SAWS stations", () => {
  it("bundles the station list", () => {
    expect(STATIONS.length).toBeGreaterThan(250);
  });

  it("finds the nearest station in South Africa", () => {
    const n = nearestStation(-33.9249, 18.4241);
    expect(n?.station.name).toMatch(/Cape Town/);
    expect(n!.distanceKm).toBeLessThan(15);
  });

  it("has nothing for places outside the country", () => {
    expect(nearestStation(51.5, -0.12)).toBeNull();
    expect(inSouthAfrica(51.5, -0.12)).toBe(false);
    expect(inSouthAfrica(-26.2, 28.04)).toBe(true);
  });
});
