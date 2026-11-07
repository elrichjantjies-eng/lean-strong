import type { IconKey } from "@/icons/defs";

/** What the sky is doing, used to pick the animated background. */
export type SkyKind = "clear" | "partly" | "cloudy" | "fog" | "rain" | "storm" | "snow";

export type Condition = {
  label: string;
  /** Label to use at night, when different ("Clear" instead of "Sunny"). */
  night?: string;
  icon: IconKey;
  nightIcon?: IconKey;
  sky: SkyKind;
  /** 0 dry … 3 heavy: drives the amount of rain/snow in the background animation. */
  intensity: number;
};

/** WMO weather interpretation codes as used by Open-Meteo. */
const TABLE: Record<number, Condition> = {
  0: {
    label: "Sunny",
    night: "Clear",
    icon: "clear-day",
    nightIcon: "clear-night",
    sky: "clear",
    intensity: 0,
  },
  1: {
    label: "Mostly sunny",
    night: "Mostly clear",
    icon: "partly-day",
    nightIcon: "partly-night",
    sky: "clear",
    intensity: 0,
  },
  2: {
    label: "Partly cloudy",
    icon: "partly-day",
    nightIcon: "partly-night",
    sky: "partly",
    intensity: 0,
  },
  3: { label: "Cloudy", icon: "cloudy", sky: "cloudy", intensity: 0 },
  45: { label: "Fog", icon: "fog", sky: "fog", intensity: 0 },
  48: { label: "Freezing fog", icon: "fog", sky: "fog", intensity: 0 },
  51: { label: "Light drizzle", icon: "drizzle", sky: "rain", intensity: 1 },
  53: { label: "Drizzle", icon: "drizzle", sky: "rain", intensity: 1 },
  55: { label: "Heavy drizzle", icon: "drizzle", sky: "rain", intensity: 2 },
  56: { label: "Freezing drizzle", icon: "sleet", sky: "rain", intensity: 1 },
  57: { label: "Freezing drizzle", icon: "sleet", sky: "rain", intensity: 2 },
  61: { label: "Light rain", icon: "rain", sky: "rain", intensity: 1 },
  63: { label: "Rain", icon: "rain", sky: "rain", intensity: 2 },
  65: { label: "Heavy rain", icon: "heavy-rain", sky: "rain", intensity: 3 },
  66: { label: "Freezing rain", icon: "sleet", sky: "rain", intensity: 2 },
  67: { label: "Freezing rain", icon: "sleet", sky: "rain", intensity: 3 },
  71: { label: "Light snow", icon: "snow", sky: "snow", intensity: 1 },
  73: { label: "Snow", icon: "snow", sky: "snow", intensity: 2 },
  75: { label: "Heavy snow", icon: "snow", sky: "snow", intensity: 3 },
  77: { label: "Snow grains", icon: "snow", sky: "snow", intensity: 1 },
  80: {
    label: "Showers",
    icon: "showers-day",
    nightIcon: "showers-night",
    sky: "rain",
    intensity: 1,
  },
  81: {
    label: "Showers",
    icon: "showers-day",
    nightIcon: "showers-night",
    sky: "rain",
    intensity: 2,
  },
  82: { label: "Heavy showers", icon: "heavy-rain", sky: "rain", intensity: 3 },
  85: { label: "Snow showers", icon: "snow", sky: "snow", intensity: 1 },
  86: { label: "Heavy snow showers", icon: "snow", sky: "snow", intensity: 3 },
  95: { label: "Thunderstorms", icon: "thunder", sky: "storm", intensity: 2 },
  96: { label: "Thunderstorms with hail", icon: "thunder", sky: "storm", intensity: 3 },
  99: { label: "Severe thunderstorms", icon: "thunder", sky: "storm", intensity: 3 },
};

export function condition(code: number): Condition {
  return TABLE[code] ?? TABLE[3];
}

export function conditionLabel(code: number, isDay = true): string {
  const c = condition(code);
  return isDay ? c.label : (c.night ?? c.label);
}

export function conditionIcon(code: number, isDay = true): IconKey {
  const c = condition(code);
  return isDay ? c.icon : (c.nightIcon ?? c.icon);
}

export function isWet(code: number): boolean {
  return code >= 51;
}

export function isStorm(code: number): boolean {
  return code >= 95;
}
