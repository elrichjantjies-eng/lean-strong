const R = 6371;
const rad = (d: number) => (d * Math.PI) / 180;

export function distanceKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** Initial bearing from point 1 to point 2, degrees clockwise from north. */
export function bearing(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const y = Math.sin(rad(lon2 - lon1)) * Math.cos(rad(lat2));
  const x =
    Math.cos(rad(lat1)) * Math.sin(rad(lat2)) -
    Math.sin(rad(lat1)) * Math.cos(rad(lat2)) * Math.cos(rad(lon2 - lon1));
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}

const POINTS16 = [
  "N",
  "NNE",
  "NE",
  "ENE",
  "E",
  "ESE",
  "SE",
  "SSE",
  "S",
  "SSW",
  "SW",
  "WSW",
  "W",
  "WNW",
  "NW",
  "NNW",
];
const POINTS8 = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];

export function compass(deg: number, points: 8 | 16 = 8): string {
  const list = points === 16 ? POINTS16 : POINTS8;
  const i = Math.round((((deg % 360) + 360) % 360) / (360 / list.length)) % list.length;
  return list[i];
}

const NAMES: Record<string, string> = {
  N: "north",
  NE: "north-east",
  E: "east",
  SE: "south-east",
  S: "south",
  SW: "south-west",
  W: "west",
  NW: "north-west",
};

export function compassName(deg: number): string {
  return NAMES[compass(deg)];
}

/** Ray casting; ring is [lat, lon] pairs. */
export function pointInPolygon(lat: number, lon: number, ring: [number, number][]): boolean {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [yi, xi] = ring[i];
    const [yj, xj] = ring[j];
    if (yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

/** Shortest distance (km) from a point to a polygon's edge; 0 when inside. */
export function distanceToPolygonKm(lat: number, lon: number, ring: [number, number][]): number {
  if (ring.length === 0) return Infinity;
  if (pointInPolygon(lat, lon, ring)) return 0;
  // Work in a local equirectangular projection: fine for the few-km scales of storm cells.
  const kx = 111.32 * Math.cos(rad(lat));
  const ky = 110.57;
  let best = Infinity;
  for (let i = 0; i < ring.length; i++) {
    const [aLat, aLon] = ring[i];
    const [bLat, bLon] = ring[(i + 1) % ring.length];
    const ax = (aLon - lon) * kx,
      ay = (aLat - lat) * ky;
    const bx = (bLon - lon) * kx,
      by = (bLat - lat) * ky;
    const dx = bx - ax,
      dy = by - ay;
    const len2 = dx * dx + dy * dy;
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
    const px = ax + t * dx,
      py = ay + t * dy;
    best = Math.min(best, Math.hypot(px, py));
  }
  return best;
}

export function centroid(ring: [number, number][]): [number, number] {
  let lat = 0,
    lon = 0;
  for (const [a, b] of ring) {
    lat += a;
    lon += b;
  }
  return [lat / ring.length, lon / ring.length];
}
