/**
 * Turns the South African Weather Service's API rows (weathersa.co.za/api/...) into the app's
 * types. Pure functions with no imports, so the snapshot script (scripts/saws-snapshot.mjs) can
 * use exactly the same parsing under plain Node.
 */
import type {
  FireDanger,
  SawsCity,
  SawsDay,
  SawsWarning,
  StormCell,
  WarningColor,
} from "./types.ts";

export type RawRow = Record<string, string | number | null | undefined | unknown>;

const NA = new Set(["", "n/a", "na", "null", "undefined", "-", "none"]);

export function str(v: unknown): string {
  if (v == null) return "";
  const s = String(v).trim();
  return NA.has(s.toLowerCase()) ? "" : s;
}

export function num(v: unknown): number | null {
  const s = str(v).replace("%", "").replace(",", ".");
  if (!s) return null;
  const n = Number.parseFloat(s);
  return Number.isFinite(n) ? n : null;
}

/** "1900-01-01T05:39:00" → "05:39" */
export function clock(v: unknown): string {
  const m = /(\d{1,2}):(\d{2})/.exec(str(v));
  return m ? `${m[1].padStart(2, "0")}:${m[2]}` : "";
}

export function sentence(v: unknown): string {
  const s = str(v);
  return s ? s[0].toUpperCase() + s.slice(1) : "";
}

const FIRE: Record<string, FireDanger> = {
  "#0066ff": "blue",
  "#0000ff": "blue",
  "#00ff00": "green",
  "#ffff00": "yellow",
  "#ff8000": "orange",
  "#ffa500": "orange",
  "#ff0000": "red",
};

export function fireDanger(v: unknown): FireDanger | null {
  return FIRE[str(v).toLowerCase()] ?? null;
}

/** SAWS times carry no zone; they're South African Standard Time (UTC+2). */
export function sastToUnix(v: unknown): number {
  const s = str(v);
  if (!s) return 0;
  const iso = /[zZ]|[+-]\d{2}:?\d{2}$/.test(s) ? s : `${s}+02:00`;
  const t = Date.parse(iso);
  return Number.isNaN(t) ? 0 : Math.round(t / 1000);
}

export function parseDay(row: RawRow): SawsDay {
  const tides: SawsDay["tides"] = [];
  const tide = (label: string, time: unknown, height: unknown) => {
    const t = clock(time);
    if (t) tides.push({ label, time: t, height: num(height) });
  };
  tide("High", row.highTideAM, row.highTideAM_Height);
  tide("Low", row.lowTideAM, row.lowTideAM_Height);
  tide("High", row.highTidePM, row.highTidePM_Height);
  tide("Low", row.lowTidePM, row.lowTidePM_Height);
  tides.sort((a, b) => a.time.localeCompare(b.time));
  const swellHeight = num(row.forecastedSwellHeight);
  return {
    date: str(row.dateT).slice(0, 10),
    min: num(row.minimumTemp),
    max: num(row.maximumTemp),
    description: sentence(row.dailyWeatherDescription),
    pop: num(row.precipitationPropability),
    precipMm: num(row.precipitationAmount),
    windDir: str(row.dailyWindDirectionShort),
    windKmh: num(row.dailyWindSpeed),
    windText: sentence(row.dailyWindDescription),
    humidityMin: num(row.minimumHumidity),
    humidityMax: num(row.maximumHumidity),
    uv: str(row.ultraVioletIndex),
    fire: fireDanger(row.fireDangerIndex),
    sunrise: clock(row.sunrise),
    sunset: clock(row.sunset),
    moonrise: clock(row.moonrise),
    moonset: clock(row.moonset),
    moonPhase: str(row.moonPhase),
    tides,
    swell:
      swellHeight == null
        ? null
        : {
            height: swellHeight,
            period: num(row.forecastedSwellPeriod),
            dir: str(row.forecastedSwellDirectionShort),
          },
  };
}

/** One entry per day (the API can repeat a day once per forecast period), in date order. */
export function parseDays(rows: RawRow[]): SawsDay[] {
  const byDate = new Map<string, SawsDay>();
  for (const row of rows) {
    if (num(row.maximumTemp) == null && num(row.minimumTemp) == null) continue;
    const day = parseDay(row);
    if (day.date && !byDate.has(day.date)) byDate.set(day.date, day);
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
}

export function warningColor(value: string): { level: number | null; color: WarningColor } {
  if (/advisory/i.test(value)) return { level: null, color: "advisory" };
  const m = /(\d+)/.exec(value);
  const level = m ? Number(m[1]) : null;
  if (level == null) return { level, color: "yellow" };
  if (level >= 10) return { level, color: "red" };
  if ([5, 6, 8, 9].includes(level)) return { level, color: "orange" };
  return { level, color: "yellow" };
}

function parseRing(polygon: unknown): [number, number][] {
  return str(polygon)
    .split(/\s+/)
    .filter((p) => p.includes(","))
    .map((p) => p.split(",").map(Number) as [number, number])
    .filter(([a, b]) => Number.isFinite(a) && Number.isFinite(b));
}

function hash(s: string): string {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return (h >>> 0).toString(36);
}

export function parseWarnings(raw: unknown): SawsWarning[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((r: RawRow) => {
      const value = str(r.value) || str(r.level);
      const { level, color } = warningColor(value);
      const ring = parseRing(r.polygon);
      const start = sastToUnix(r.startTime ?? r.effective ?? r.onset);
      const end = sastToUnix(r.expires ?? r.endTime);
      const event = str(r.eevent) || str(r.event) || "Weather warning";
      const headline = str(r.headline) || event;
      const area = str(r.areaDesc);
      const id = str(r.identifier) || str(r.id) || hash(`${event}|${headline}|${area}|${start}`);
      return {
        id,
        event,
        level,
        color,
        area,
        headline,
        description: str(r.description),
        instruction: str(r.instruction),
        start,
        end,
        polygons: ring.length >= 3 ? [ring] : [],
      };
    })
    .filter((w) => w.headline);
}

export function parseStorms(raw: unknown): StormCell[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((r: RawRow) => {
      const pts = Array.isArray(r.points) ? (r.points as unknown[][]) : [];
      const polygon = pts
        .map((p) => [num(p[0]), num(p[1])] as [number | null, number | null])
        .filter((p): p is [number, number] => p[0] != null && p[1] != null);
      const instance = str(r.instance).toLowerCase();
      return {
        id: str(r.stormID),
        instance: (instance === "forecast30" || instance === "forecast60"
          ? instance
          : "current") as StormCell["instance"],
        time: sastToUnix(r.instanceTime),
        polygon,
      };
    })
    .filter((s) => s.polygon.length >= 3);
}

export function parseCities(raw: unknown): SawsCity[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((r: RawRow) => ({
      station: {
        synop: str(r.synopNo),
        name: str(r.stationName),
        province: str(r.province),
        municipality: str(r.municipality),
        lat: num(r.latitude) ?? NaN,
        lon: num(r.longitude) ?? NaN,
      },
      max: num(r.maximumTemp),
      min: num(r.minimumTemp),
      description: sentence(r.dailyWeatherDescription),
    }))
    .filter(
      (c) => c.station.name && Number.isFinite(c.station.lat) && Number.isFinite(c.station.lon),
    );
}
