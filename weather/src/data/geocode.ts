import type { Place } from "./types";
import { getJson } from "./http";

type OmPlace = {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  admin1?: string;
  country?: string;
  country_code?: string;
  population?: number;
};

export function placeId(lat: number, lon: number): string {
  return `${lat.toFixed(3)},${lon.toFixed(3)}`;
}

/** Search places by name. South African matches are listed first. */
export async function searchPlaces(query: string): Promise<Place[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const params = new URLSearchParams({ name: q, count: "15", language: "en", format: "json" });
  const r = await getJson<{ results?: OmPlace[] }>(
    `https://geocoding-api.open-meteo.com/v1/search?${params}`,
  );
  const list = (r.results ?? []).map((p) => ({
    id: placeId(p.latitude, p.longitude),
    name: p.name,
    admin: p.admin1,
    country: p.country,
    countryCode: p.country_code,
    lat: p.latitude,
    lon: p.longitude,
    pop: p.population ?? 0,
  }));
  list.sort(
    (a, b) => Number(b.countryCode === "ZA") - Number(a.countryCode === "ZA") || b.pop - a.pop,
  );
  return list.map(({ pop: _pop, ...p }) => p);
}

type BdcResponse = {
  city?: string;
  locality?: string;
  principalSubdivision?: string;
  countryName?: string;
  countryCode?: string;
};

/** A friendly name for coordinates (suburb or town). */
export async function reverseGeocode(lat: number, lon: number): Promise<Partial<Place>> {
  const params = new URLSearchParams({
    latitude: String(lat),
    longitude: String(lon),
    localityLanguage: "en",
  });
  const r = await getJson<BdcResponse>(
    `https://api.bigdatacloud.net/data/reverse-geocode-client?${params}`,
    8000,
  );
  return {
    name: r.city || r.locality || undefined,
    admin: r.principalSubdivision || undefined,
    country: r.countryName,
    countryCode: r.countryCode,
  };
}
