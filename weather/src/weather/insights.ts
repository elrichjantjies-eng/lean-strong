/**
 * Plain-language insights derived from the forecast: the hourly headline, the rain nowcast,
 * yesterday comparison, UV advice, AQI categories, pressure trend and so on.
 */
import type { Day, Forecast, Hour, Minutely } from "@/data/types";
import { condition, conditionLabel, isStorm, isWet } from "./codes";
import { fmtTime, hourOfDay } from "@/lib/time";
import { compassName } from "@/lib/geo";
import type { Fmt } from "@/lib/units";

/** Index of the hour containing `now`. */
export function currentHourIndex(hourly: Hour[], now: number): number {
  let i = hourly.findIndex((h) => h.time > now) - 1;
  if (i < 0) i = hourly.length - 1;
  return Math.max(0, i);
}

/** The next `count` hours starting with the current one. */
export function upcomingHours(f: Forecast, now: number, count = 24): Hour[] {
  const i = currentHourIndex(f.hourly, now);
  return f.hourly.slice(i, i + count);
}

/** Today in the daily list (index 1 normally; 0 is yesterday). */
export function todayIndex(f: Forecast, now: number): number {
  for (let i = f.daily.length - 1; i >= 0; i--) if (f.daily[i].time <= now) return i;
  return 0;
}

export function today(f: Forecast, now: number): Day {
  return f.daily[todayIndex(f, now)];
}

// ---- Rain nowcast --------------------------------------------------------------------------

export type Nowcast = {
  kind: "starting" | "stopping" | "continuing";
  text: string;
  at: number | null;
} | null;

const WET_MM = 0.1;

/** "Rain starting around 14:45" from the 15-minute precipitation forecast. */
export function nowcast(minutely: Minutely[], now: number, offset: number, h24: boolean): Nowcast {
  const next = minutely.filter((m) => m.time + 900 > now);
  if (next.length < 2) return null;
  const wetNow = next[0].precip >= WET_MM;
  if (!wetNow) {
    const start = next.find((m) => m.precip >= WET_MM);
    if (!start) return null;
    const mins = Math.max(0, Math.round((start.time - now) / 60 / 5) * 5);
    return {
      kind: "starting",
      at: start.time,
      text:
        mins <= 10
          ? "Rain starting in the next few minutes"
          : mins < 60
            ? `Rain starting in about ${mins} min`
            : `Rain expected around ${fmtTime(start.time, offset, h24)}`,
    };
  }
  const stop = next.find((m) => m.precip < WET_MM);
  if (!stop)
    return { kind: "continuing", at: null, text: "Rain continuing for the next few hours" };
  const mins = Math.max(5, Math.round((stop.time - now) / 60 / 5) * 5);
  return {
    kind: "stopping",
    at: stop.time,
    text:
      mins < 60
        ? `Rain stopping in about ${mins} min`
        : `Rain easing around ${fmtTime(stop.time, offset, h24)}`,
  };
}

// ---- Headline ------------------------------------------------------------------------------

/** One sentence for the top of the hourly card, like the iPhone app. */
export function headline(f: Forecast, now: number, fmt: Fmt): string {
  const hours = upcomingHours(f, now, 24);
  const off = f.utcOffset;
  const h24 = fmt.s.h24;
  const cur = f.current;
  const parts: string[] = [];

  const storm = hours.find((h) => isStorm(h.code));
  const wet = hours.find((h) => isWet(h.code) && h.pop >= 40);
  if (storm) {
    parts.push(
      storm.time <= now + 3600
        ? "Thunderstorms around now."
        : `Thunderstorms possible from ${fmtTime(storm.time, off, h24)}.`,
    );
  } else if (isWet(cur.code)) {
    const dry = hours.find((h) => !isWet(h.code) && h.pop < 30);
    parts.push(
      dry
        ? `${conditionLabel(cur.code)} easing around ${fmtTime(dry.time, off, h24)}.`
        : `${conditionLabel(cur.code)} for the rest of the day.`,
    );
  } else if (wet) {
    parts.push(
      `${condition(wet.code).label} likely from ${fmtTime(wet.time, off, h24)} (${wet.pop}%).`,
    );
  } else {
    // Describe the coming change in sky, if any.
    const curSky = condition(cur.code).sky;
    const change = hours.slice(2).find((h) => condition(h.code).sky !== curSky);
    if (change) {
      parts.push(
        `${conditionLabel(change.code, change.isDay)} conditions expected around ${fmtTime(change.time, off, h24)}.`,
      );
    } else {
      parts.push(
        `${conditionLabel(cur.code, cur.isDay)} conditions will continue for the rest of the day.`,
      );
    }
  }

  const gust = Math.max(...hours.slice(0, 12).map((h) => h.gust));
  if (gust >= 45) {
    const dir = compassName(hours.find((h) => h.gust === gust)?.windDir ?? cur.windDir);
    parts.push(`Wind gusts up to ${fmt.wind(gust)} from the ${dir}.`);
  }
  return parts.join(" ");
}

/** "Today is 3° warmer than yesterday." */
export function vsYesterday(f: Forecast, now: number, fmt: Fmt): string | null {
  const ti = todayIndex(f, now);
  if (ti < 1) return null;
  const diff = f.daily[ti].max - f.daily[ti - 1].max;
  const unitDiff = fmt.s.temp === "f" ? diff * 1.8 : diff;
  const r = Math.round(unitDiff);
  if (Math.abs(r) < 2) return "Similar temperatures to yesterday.";
  return `${Math.abs(r)}° ${r > 0 ? "warmer" : "cooler"} than yesterday.`;
}

/** Explains the feels-like temperature. */
export function feelsLikeNote(temp: number, feels: number, humidity: number, wind: number): string {
  const d = feels - temp;
  if (Math.abs(d) < 1.5) return "Similar to the actual temperature.";
  if (d < 0)
    return wind >= 15
      ? "Wind is making it feel cooler."
      : "Feels cooler than the actual temperature.";
  return humidity >= 60
    ? "Humidity is making it feel warmer."
    : "Sunshine is making it feel warmer.";
}

export function dewNote(dew: number): string {
  if (dew >= 21) return `The dew point is ${Math.round(dew)}° right now: it feels muggy.`;
  if (dew >= 16) return `The dew point is ${Math.round(dew)}° right now: slightly humid.`;
  if (dew >= 10) return `The dew point is ${Math.round(dew)}° right now: comfortable.`;
  return `The dew point is ${Math.round(dew)}° right now: dry air.`;
}

export function visibilityNote(m: number): string {
  if (m >= 20000) return "Perfectly clear view.";
  if (m >= 10000) return "Clear view.";
  if (m >= 4000) return "Haze is slightly reducing visibility.";
  if (m >= 1000) return "Mist or haze is reducing visibility.";
  return "Fog: drive carefully.";
}

// ---- Pressure ------------------------------------------------------------------------------

export function pressureTrend(
  f: Forecast,
  now: number,
): { trend: "rising" | "falling" | "steady"; delta: number } {
  const i = currentHourIndex(f.hourly, now);
  const past = f.hourly[Math.max(0, i - 3)]?.pressure ?? f.current.pressure;
  const ahead = f.hourly[Math.min(f.hourly.length - 1, i + 3)]?.pressure ?? f.current.pressure;
  const delta = ahead - past;
  return { trend: delta > 1 ? "rising" : delta < -1 ? "falling" : "steady", delta };
}

// ---- UV ------------------------------------------------------------------------------------

export function uvCategory(uv: number): { label: string; color: string } {
  if (uv < 3) return { label: "Low", color: "#4ade80" };
  if (uv < 6) return { label: "Moderate", color: "#facc15" };
  if (uv < 8) return { label: "High", color: "#fb923c" };
  if (uv < 11) return { label: "Very high", color: "#ef4444" };
  return { label: "Extreme", color: "#a855f7" };
}

/** "Use sun protection 09:00–15:00." from today's hourly UV. */
export function uvAdvice(f: Forecast, now: number, h24: boolean): string {
  const day = today(f, now);
  const hours = f.hourly.filter((h) => h.time >= day.time && h.time < day.time + 86400);
  const high = hours.filter((h) => h.uv >= 3);
  if (high.length === 0) return "Low for the rest of the day.";
  const from = high[0].time;
  const to = high[high.length - 1].time + 3600;
  if (to <= now) return "Low for the rest of the day.";
  return `Use sun protection ${fmtTime(Math.max(from, now - (now % 3600)), f.utcOffset, h24)}–${fmtTime(to, f.utcOffset, h24)}.`;
}

// ---- Air quality -----------------------------------------------------------------------------

export function aqiCategory(
  aqi: number,
  scale: "us" | "eu",
): { label: string; color: string; advice: string } {
  const us = [
    [50, "Good", "#4ade80", "Air quality is good. Enjoy being outside."],
    [
      100,
      "Moderate",
      "#facc15",
      "Unusually sensitive people should consider limiting long outdoor exertion.",
    ],
    [
      150,
      "Unhealthy for sensitive groups",
      "#fb923c",
      "Sensitive groups should reduce prolonged outdoor exertion.",
    ],
    [200, "Unhealthy", "#ef4444", "Everyone should reduce prolonged outdoor exertion."],
    [300, "Very unhealthy", "#a855f7", "Avoid prolonged outdoor exertion."],
    [Infinity, "Hazardous", "#9f1239", "Stay indoors and keep activity low."],
  ] as const;
  const eu = [
    [20, "Good", "#4ade80", "Air quality is good. Enjoy being outside."],
    [40, "Fair", "#a3e635", "Air quality is acceptable."],
    [
      60,
      "Moderate",
      "#facc15",
      "Sensitive people should consider limiting intense outdoor activity.",
    ],
    [80, "Poor", "#fb923c", "Consider reducing intense outdoor activity."],
    [100, "Very poor", "#ef4444", "Reduce outdoor activity."],
    [Infinity, "Extremely poor", "#a855f7", "Avoid outdoor activity."],
  ] as const;
  const table = scale === "us" ? us : eu;
  const row = table.find(([max]) => aqi <= max) ?? table[table.length - 1];
  return { label: row[1], color: row[2], advice: row[3] };
}

// ---- Daylight ------------------------------------------------------------------------------

/** "+1m 12s more daylight than yesterday" */
export function daylightChange(f: Forecast, now: number): string | null {
  const ti = todayIndex(f, now);
  if (ti < 1) return null;
  const diff = Math.round(f.daily[ti].daylight - f.daily[ti - 1].daylight);
  const m = Math.floor(Math.abs(diff) / 60);
  const s = Math.abs(diff) % 60;
  const amount = m > 0 ? `${m}m ${s}s` : `${s}s`;
  return `${amount} ${diff >= 0 ? "more" : "less"} daylight than yesterday`;
}

/** 0–1 position of the sun between sunrise and sunset (null at night). */
export function sunProgress(day: Day, now: number): number | null {
  if (now < day.sunrise || now > day.sunset) return null;
  return (now - day.sunrise) / (day.sunset - day.sunrise);
}

/** Morning and evening golden hours (approximate: the hour after sunrise / before sunset). */
export function goldenHours(day: Day): { morning: [number, number]; evening: [number, number] } {
  return { morning: [day.sunrise, day.sunrise + 3600], evening: [day.sunset - 3600, day.sunset] };
}

// ---- Precipitation totals --------------------------------------------------------------------

export function precipTotals(f: Forecast, now: number): { past24: number; next24: number } {
  let past24 = 0;
  let next24 = 0;
  for (const h of f.hourly) {
    if (h.time < now && h.time >= now - 86400) past24 += h.precip;
    if (h.time >= now && h.time < now + 86400) next24 += h.precip;
  }
  return { past24, next24 };
}

// ---- Clothing / what to take ------------------------------------------------------------------

export function whatToTake(f: Forecast, now: number): string[] {
  const hours = upcomingHours(f, now, 12);
  const out: string[] = [];
  const maxPop = Math.max(...hours.map((h) => h.pop));
  const minFeels = Math.min(...hours.map((h) => h.feels));
  const maxFeels = Math.max(...hours.map((h) => h.feels));
  const maxUv = Math.max(...hours.map((h) => h.uv));
  const maxGust = Math.max(...hours.map((h) => h.gust));
  if (maxPop >= 50) out.push("☂️ Umbrella");
  else if (maxPop >= 30) out.push("🌂 Maybe an umbrella");
  if (minFeels < 8) out.push("🧥 Warm coat");
  else if (minFeels < 15) out.push("🧶 Jacket");
  if (maxFeels >= 28) out.push("💧 Water");
  if (maxUv >= 6) out.push("🧴 Sunscreen");
  if (maxUv >= 3 && hours.some((h) => h.isDay && h.cloud < 50)) out.push("🕶️ Sunglasses");
  if (maxGust >= 50) out.push("🧢 Secure loose items");
  return out;
}

export function hourOf(t: number, f: Forecast): number {
  return hourOfDay(t, f.utcOffset);
}
