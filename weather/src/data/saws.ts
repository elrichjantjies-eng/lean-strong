/**
 * South African Weather Service data (https://www.weathersa.co.za).
 *
 * SAWS's API doesn't send CORS headers, so a browser can't read it directly:
 *  - in the Android app, requests go through the native HTTP stack (live data);
 *  - on the web (iPhone), the app reads a snapshot that a GitHub Action refreshes every two
 *    hours and publishes next to the app (see scripts/saws-snapshot.mjs).
 */
import stationRows from "./saws-stations.json";
import type { SawsBundle, SawsCity, SawsForecast, SawsStation } from "./types";
import { canReadSawsLive, getJson, getNativeText } from "./http";
import { parseCities, parseDays, parseStorms, parseWarnings, type RawRow } from "./sawsParse";
import { distanceKm } from "@/lib/geo";

export const SAWS_SITE = "https://www.weathersa.co.za";
const API = `${SAWS_SITE}/api`;
const SNAPSHOT = `${import.meta.env.BASE_URL}saws`;

export const STATIONS: SawsStation[] = (
  stationRows as [string, string, string, string, number, number][]
).map(([synop, name, province, municipality, lat, lon]) => ({
  synop,
  name,
  province,
  municipality,
  lat,
  lon,
}));

/** The closest SAWS forecast station, if the place is in (or right next to) South Africa. */
export function nearestStation(
  lat: number,
  lon: number,
  maxKm = 80,
): { station: SawsStation; distanceKm: number } | null {
  let best: SawsStation | null = null;
  let bestKm = Infinity;
  for (const s of STATIONS) {
    const d = distanceKm(lat, lon, s.lat, s.lon);
    if (d < bestKm) {
      best = s;
      bestKm = d;
    }
  }
  return best && bestKm <= maxKm ? { station: best, distanceKm: bestKm } : null;
}

/** Rough South Africa (+ Lesotho, Eswatini) bounding box. */
export function inSouthAfrica(lat: number, lon: number): boolean {
  return lat < -21.5 && lat > -35.5 && lon > 16 && lon < 33.5;
}

async function sawsJson(path: string): Promise<unknown> {
  const { status, body } = await getNativeText(`${API}/${path}`);
  // SAWS answers "nothing found" with HTTP 400 and a plain-text message.
  if (status === 400 || status === 404) return [];
  if (status < 200 || status >= 300) throw new Error(`SAWS ${status}`);
  try {
    return JSON.parse(body);
  } catch {
    return [];
  }
}

export async function fetchSawsForecast(
  station: SawsStation,
  distance: number,
): Promise<SawsForecast> {
  if (canReadSawsLive()) {
    const rows = (await sawsJson(
      `DailyForecast/stationName?stationName=${encodeURIComponent(station.name)}`,
    )) as RawRow[];
    const mine = rows.filter((r) => !r.synopNo || String(r.synopNo) === station.synop);
    return {
      fetchedAt: Date.now(),
      station,
      distanceKm: distance,
      days: parseDays(mine),
      source: "live",
    };
  }
  const snap = await getJson<{ fetchedAt: number; days: SawsForecast["days"] }>(
    `${SNAPSHOT}/forecast/${station.synop}.json`,
  );
  return {
    fetchedAt: snap.fetchedAt,
    station,
    distanceKm: distance,
    days: snap.days,
    source: "snapshot",
  };
}

/** Current warnings and thunderstorm cells for the whole country. */
export async function fetchSawsBundle(): Promise<SawsBundle> {
  if (canReadSawsLive()) {
    const [warnings, storms] = await Promise.all([
      sawsJson("WarningAlerts").then(parseWarnings),
      sawsJson("StormTrack")
        .then(parseStorms)
        .catch(() => []),
    ]);
    return { fetchedAt: Date.now(), warnings, storms, source: "live" };
  }
  const snap = await getJson<Omit<SawsBundle, "source">>(`${SNAPSHOT}/alerts.json`);
  return { ...snap, source: "snapshot" };
}

/** Today's official forecast for the main towns, for the map. */
export async function fetchSawsCities(): Promise<SawsCity[]> {
  if (canReadSawsLive()) return parseCities(await sawsJson("CurrentWeather"));
  const snap = await getJson<{ cities: SawsCity[] }>(`${SNAPSHOT}/cities.json`);
  return snap.cities;
}
