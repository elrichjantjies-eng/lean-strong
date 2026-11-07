import { useMemo } from "react";
import type { Place, SawsForecast, SawsWarning, StormCell } from "@/data/types";
import { useCached } from "@/data/cache";
import { fetchAirQuality, fetchForecast, fetchMarine } from "@/data/openMeteo";
import {
  fetchSawsBundle,
  fetchSawsForecast,
  inSouthAfrica,
  nearestStation,
  STATIONS,
} from "@/data/saws";
import { distanceKm, distanceToPolygonKm, pointInPolygon, bearing, centroid } from "@/lib/geo";
import { useNow } from "@/lib/useNow";

const MIN = 60_000;

export function useForecast(place: Place | undefined) {
  const key = place ? `fc:${place.lat.toFixed(3)},${place.lon.toFixed(3)}` : null;
  return useCached(key, () => fetchForecast(place!.lat, place!.lon), 15 * MIN);
}

export function useAirQuality(place: Place | undefined) {
  const key = place ? `aq:${place.lat.toFixed(2)},${place.lon.toFixed(2)}` : null;
  return useCached(key, () => fetchAirQuality(place!.lat, place!.lon), 60 * MIN);
}

export function useMarine(place: Place | undefined) {
  const key = place ? `mr:${place.lat.toFixed(2)},${place.lon.toFixed(2)}` : null;
  return useCached(key, () => fetchMarine(place!.lat, place!.lon), 90 * MIN);
}

/** Official SAWS forecast from the nearest station (South Africa only). */
export function useSawsForecast(place: Place | undefined) {
  const near = place ? nearestStation(place.lat, place.lon) : null;
  const key = near ? `saws:${near.station.synop}` : null;
  return {
    near,
    ...useCached<SawsForecast>(
      key,
      async () => {
        // Some stations have no forecast today; fall back to the next closest ones.
        const candidates = STATIONS.map((s) => ({
          s,
          d: distanceKm(place!.lat, place!.lon, s.lat, s.lon),
        }))
          .sort((a, b) => a.d - b.d)
          .slice(0, 4);
        let last: SawsForecast | null = null;
        for (const c of candidates) {
          try {
            const f = await fetchSawsForecast(c.s, c.d);
            if (f.days.length) return f;
            last = f;
          } catch (e) {
            if (c === candidates[candidates.length - 1]) throw e;
          }
        }
        if (last) return last;
        throw new Error("No SAWS forecast nearby");
      },
      2 * 60 * MIN,
    ),
  };
}

/** Country-wide SAWS warnings and storm cells (one shared request). */
export function useSawsBundle(enabled: boolean) {
  return useCached(enabled ? "saws:bundle" : null, fetchSawsBundle, 15 * MIN);
}

export type StormNearby = {
  distanceKm: number;
  bearing: number;
  instance: StormCell["instance"];
  time: number;
};

/** Warnings whose area covers the place, and the closest thunderstorm cell within 60 km. */
export function useLocalAlerts(place: Place | undefined) {
  const sa = !!place && inSouthAfrica(place.lat, place.lon);
  const bundle = useSawsBundle(sa);
  const data = bundle.data;
  const now = useNow();
  const result = useMemo(() => {
    if (!place || !data)
      return { warnings: [] as SawsWarning[], storm: null as StormNearby | null };
    const warnings = data.warnings.filter(
      (w) =>
        (!w.end || w.end > now) &&
        (w.polygons.length === 0
          ? false
          : w.polygons.some((ring) => pointInPolygon(place.lat, place.lon, ring))),
    );
    let storm: StormNearby | null = null;
    for (const cell of data.storms) {
      if (cell.instance === "forecast60") continue;
      const d = distanceToPolygonKm(place.lat, place.lon, cell.polygon);
      if (d <= 60 && (!storm || d < storm.distanceKm)) {
        const [clat, clon] = centroid(cell.polygon);
        storm = {
          distanceKm: d,
          bearing: bearing(place.lat, place.lon, clat, clon),
          instance: cell.instance,
          time: cell.time,
        };
      }
    }
    // Ignore stale storm data (the snapshot can be up to a couple of hours old).
    if (storm && now - storm.time > 90 * 60) storm = null;
    return { warnings, storm };
  }, [place, data, now]);
  return { ...result, bundle };
}
