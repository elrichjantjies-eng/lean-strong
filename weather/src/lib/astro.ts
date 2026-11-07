/**
 * Low-precision sun and moon positions (after the formulas in Meeus' "Astronomical Algorithms",
 * as popularised by SunCalc). Good to a minute or two, which is plenty for a weather app.
 */

const PI = Math.PI;
const rad = PI / 180;
const e = rad * 23.4397; // obliquity of the Earth
const J2000 = 2451545;

const toDays = (unixSec: number) => unixSec / 86400 + 2440587.5 - J2000;

function rightAscension(l: number, b: number) {
  return Math.atan2(Math.sin(l) * Math.cos(e) - Math.tan(b) * Math.sin(e), Math.cos(l));
}
function declination(l: number, b: number) {
  return Math.asin(Math.sin(b) * Math.cos(e) + Math.cos(b) * Math.sin(e) * Math.sin(l));
}
function siderealTime(d: number, lw: number) {
  return rad * (280.16 + 360.9856235 * d) - lw;
}

function sunCoords(d: number) {
  const M = rad * (357.5291 + 0.98560028 * d);
  const C = rad * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M));
  const L = M + C + rad * 102.9372 + PI;
  return { dec: declination(L, 0), ra: rightAscension(L, 0) };
}

function moonCoords(d: number) {
  const L = rad * (218.316 + 13.176396 * d);
  const M = rad * (134.963 + 13.064993 * d);
  const F = rad * (93.272 + 13.22935 * d);
  const l = L + rad * 6.289 * Math.sin(M);
  const b = rad * 5.128 * Math.sin(F);
  const dist = 385001 - 20905 * Math.cos(M);
  return { ra: rightAscension(l, b), dec: declination(l, b), dist };
}

/** Moon altitude above the horizon, in degrees. */
export function moonAltitude(unixSec: number, lat: number, lon: number): number {
  const d = toDays(unixSec);
  const lw = rad * -lon;
  const phi = rad * lat;
  const c = moonCoords(d);
  const H = siderealTime(d, lw) - c.ra;
  let h = Math.asin(
    Math.sin(phi) * Math.sin(c.dec) + Math.cos(phi) * Math.cos(c.dec) * Math.cos(H),
  );
  // Parallax: the moon is close enough that our position on the globe shifts it.
  const pa = Math.asin(6378.14 / c.dist);
  h -= pa * Math.cos(h);
  return h / rad;
}

export type MoonInfo = {
  /** 0 new → 0.25 first quarter → 0.5 full → 0.75 last quarter → 1 new */
  phase: number;
  /** Lit fraction 0–1. */
  fraction: number;
  name: string;
  /** Days since new moon. */
  age: number;
};

export function moonInfo(unixSec: number): MoonInfo {
  const d = toDays(unixSec);
  const s = sunCoords(d);
  const m = moonCoords(d);
  const sdist = 149598000;
  const phi = Math.acos(
    Math.sin(s.dec) * Math.sin(m.dec) + Math.cos(s.dec) * Math.cos(m.dec) * Math.cos(s.ra - m.ra),
  );
  const inc = Math.atan2(sdist * Math.sin(phi), m.dist - sdist * Math.cos(phi));
  const angle = Math.atan2(
    Math.cos(s.dec) * Math.sin(s.ra - m.ra),
    Math.sin(s.dec) * Math.cos(m.dec) - Math.cos(s.dec) * Math.sin(m.dec) * Math.cos(s.ra - m.ra),
  );
  const fraction = (1 + Math.cos(inc)) / 2;
  const phase = 0.5 + (0.5 * inc * (angle < 0 ? -1 : 1)) / PI;
  return { phase, fraction, name: phaseName(phase), age: phase * 29.530588 };
}

export function phaseName(p: number): string {
  if (p < 0.025 || p > 0.975) return "New Moon";
  if (p < 0.225) return "Waxing Crescent";
  if (p < 0.275) return "First Quarter";
  if (p < 0.475) return "Waxing Gibbous";
  if (p < 0.525) return "Full Moon";
  if (p < 0.725) return "Waning Gibbous";
  if (p < 0.775) return "Last Quarter";
  return "Waning Crescent";
}

/** Next time (Unix seconds) the moon reaches the given phase (0 new, 0.5 full). */
export function nextPhase(from: number, target: 0 | 0.5): number {
  const norm = (p: number) => (target === 0 ? (p + 0.5) % 1 : p); // make the target 0.5
  let prev = norm(moonInfo(from).phase);
  for (let t = from + 3600; t < from + 31 * 86400; t += 3600) {
    const cur = norm(moonInfo(t).phase);
    if (prev < 0.5 && cur >= 0.5) return t;
    prev = cur;
  }
  return from + 29.53 * 86400;
}

/** Moonrise and moonset within the 24 h after `dayStart` (Unix seconds), if they happen. */
export function moonTimes(
  dayStart: number,
  lat: number,
  lon: number,
): { rise: number | null; set: number | null } {
  const hc = 0.133;
  const step = 600;
  let rise: number | null = null;
  let set: number | null = null;
  let prev = moonAltitude(dayStart, lat, lon) - hc;
  for (let t = dayStart + step; t <= dayStart + 86400; t += step) {
    const cur = moonAltitude(t, lat, lon) - hc;
    if (prev < 0 && cur >= 0 && rise == null) rise = t - step + (step * -prev) / (cur - prev);
    if (prev >= 0 && cur < 0 && set == null) set = t - step + (step * prev) / (prev - cur);
    prev = cur;
  }
  return { rise: rise && Math.round(rise), set: set && Math.round(set) };
}
