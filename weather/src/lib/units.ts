import type {
  DistanceUnit,
  PrecipUnit,
  PressureUnit,
  Settings,
  TempUnit,
  WindUnit,
} from "@/state/settings";

/** All data is fetched in metric (°C, km/h, mm, hPa, m) and converted only for display. */

export function temp(c: number, unit: TempUnit): number {
  return unit === "f" ? (c * 9) / 5 + 32 : c;
}

/** "23°" */
export function fmtTemp(c: number | null | undefined, unit: TempUnit): string {
  if (c == null || Number.isNaN(c)) return "–";
  const v = Math.round(temp(c, unit));
  return `${Object.is(v, -0) ? 0 : v}°`;
}

/** Beaufort number from km/h. */
export function beaufort(kmh: number): number {
  const limits = [1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118];
  const i = limits.findIndex((l) => kmh < l);
  return i === -1 ? 12 : i;
}

const BEAUFORT_NAMES = [
  "Calm",
  "Light air",
  "Light breeze",
  "Gentle breeze",
  "Moderate breeze",
  "Fresh breeze",
  "Strong breeze",
  "Near gale",
  "Gale",
  "Strong gale",
  "Storm",
  "Violent storm",
  "Hurricane force",
];

export function beaufortName(kmh: number): string {
  return BEAUFORT_NAMES[beaufort(kmh)];
}

export function wind(kmh: number, unit: WindUnit): number {
  switch (unit) {
    case "ms":
      return kmh / 3.6;
    case "mph":
      return kmh / 1.609344;
    case "kn":
      return kmh / 1.852;
    case "bft":
      return beaufort(kmh);
    default:
      return kmh;
  }
}

export const WIND_LABEL: Record<WindUnit, string> = {
  kmh: "km/h",
  ms: "m/s",
  mph: "mph",
  kn: "kn",
  bft: "Bft",
};

/** "24 km/h" */
export function fmtWind(kmh: number | null | undefined, unit: WindUnit, withUnit = true): string {
  if (kmh == null) return "–";
  const v = wind(kmh, unit);
  const n = unit === "ms" ? Math.round(v * 10) / 10 : Math.round(v);
  return withUnit ? `${n} ${WIND_LABEL[unit]}` : String(n);
}

export function precip(mm: number, unit: PrecipUnit): number {
  return unit === "in" ? mm / 25.4 : mm;
}

export function fmtPrecip(mm: number | null | undefined, unit: PrecipUnit): string {
  if (mm == null) return "–";
  if (unit === "in") {
    const v = mm / 25.4;
    return `${v < 0.1 && v > 0 ? v.toFixed(2) : v.toFixed(1)}″`;
  }
  return `${mm < 10 && mm % 1 !== 0 ? mm.toFixed(1) : Math.round(mm)} mm`;
}

export function fmtPressure(hpa: number | null | undefined, unit: PressureUnit): string {
  if (hpa == null) return "–";
  if (unit === "inhg") return `${(hpa * 0.02953).toFixed(2)} inHg`;
  if (unit === "mmhg") return `${Math.round(hpa * 0.750062)} mmHg`;
  return `${Math.round(hpa)} hPa`;
}

export function fmtDistance(km: number | null | undefined, unit: DistanceUnit): string {
  if (km == null) return "–";
  const v = unit === "mi" ? km / 1.609344 : km;
  const label = unit === "mi" ? "mi" : "km";
  return `${v < 10 ? Math.round(v * 10) / 10 : Math.round(v)} ${label}`;
}

/** Visibility from metres. */
export function fmtVisibility(m: number | null | undefined, unit: DistanceUnit): string {
  if (m == null) return "–";
  return fmtDistance(m / 1000, unit);
}

export type Fmt = ReturnType<typeof formatter>;

/** Bundles every formatter with the user's units so components can just call fmt.temp(x). */
export function formatter(s: Settings) {
  return {
    s,
    temp: (c: number | null | undefined) => fmtTemp(c, s.temp),
    tempUnit: s.temp === "f" ? "°F" : "°C",
    wind: (kmh: number | null | undefined, withUnit = true) => fmtWind(kmh, s.wind, withUnit),
    windUnit: WIND_LABEL[s.wind],
    precip: (mm: number | null | undefined) => fmtPrecip(mm, s.precip),
    pressure: (hpa: number | null | undefined) => fmtPressure(hpa, s.pressure),
    distance: (km: number | null | undefined) => fmtDistance(km, s.distance),
    visibility: (m: number | null | undefined) => fmtVisibility(m, s.distance),
  };
}
