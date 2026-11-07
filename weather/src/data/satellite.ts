/**
 * Live satellite layers from EUMETSAT (https://view.eumetsat.int), free and without a key.
 *
 * RainViewer's radar has no coverage over southern Africa (SAWS doesn't share its radar), so in
 * South Africa the map shows rain estimated by the Meteosat satellites instead: the H SAF
 * blended rain rate every 15 minutes, lightning from the MTG Lightning Imager, and true-colour
 * clouds. All of them cover Africa, Europe and the Atlantic.
 */

export const EUMETSAT_WMS = "https://view.eumetsat.int/geoserver/ows";

export type SatLayer = { name: string; style: string; label: string };

export const SAT = {
  rain: { name: "msg_fes:h60b", style: "", label: "Rain (satellite)" },
  lightning: { name: "mtg_fd:li_afa", style: "", label: "Lightning" },
  clouds: { name: "mtg_fd:rgb_geocolour", style: "", label: "Clouds (satellite)" },
} satisfies Record<string, SatLayer>;

/** Rough footprint of the Meteosat 0° disk, where these layers have data. */
export function inSatelliteView(lat: number, lon: number): boolean {
  return Math.abs(lat) < 72 && lon > -65 && lon < 65;
}

/** Reads `start/end/PT15M` from a WMS capabilities time dimension. */
export function parseTimeDimension(xml: string): { end: number; step: number } | null {
  const m = /<Dimension[^>]*name="time"[^>]*>\s*([^<]+)</.exec(xml);
  if (!m) return null;
  const parts = m[1].trim().split("/");
  if (parts.length !== 3) return null;
  const end = Date.parse(parts[1]);
  const p = /^PT(?:(\d+)H)?(?:(\d+)M)?$/.exec(parts[2]);
  if (Number.isNaN(end) || !p) return null;
  const step = (Number(p[1] ?? 0) * 60 + Number(p[2] ?? 0)) * 60;
  return step > 0 ? { end: Math.round(end / 1000), step } : null;
}

/** The last `count` image times (Unix seconds), oldest first. */
export function frameTimes(end: number, step: number, count: number): number[] {
  return Array.from({ length: count }, (_, i) => end - (count - 1 - i) * step);
}

/** Times of the latest satellite rain images (2 hours, every 15 minutes). */
export async function fetchSatelliteTimes(count = 9): Promise<number[]> {
  const [ws, name] = SAT.rain.name.split(":");
  try {
    const res = await fetch(
      `https://view.eumetsat.int/geoserver/${ws}/${name}/ows?service=WMS&request=GetCapabilities&version=1.3.0`,
    );
    const dim = res.ok ? parseTimeDimension(await res.text()) : null;
    if (dim) return frameTimes(dim.end, dim.step, count);
  } catch {
    /* fall through to an estimate */
  }
  // Images arrive about 20 minutes after the scan; the server snaps to the nearest one.
  const step = 15 * 60;
  const end = Math.floor((Date.now() / 1000 - 25 * 60) / step) * step;
  return frameTimes(end, step, count);
}

export const isoTime = (t: number) => new Date(t * 1000).toISOString().replace(".000", "");
