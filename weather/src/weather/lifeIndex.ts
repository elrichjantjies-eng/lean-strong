/**
 * Lifestyle indices ("is it a good day for…"), scored 0–10 from the forecast.
 */
import type { Forecast, Hour } from "@/data/types";
import { isWet } from "./codes";
import { moonInfo } from "@/lib/astro";
import { currentHourIndex, todayIndex } from "./insights";

export type Activity = {
  id: string;
  name: string;
  emoji: string;
  score: number;
  verdict: "Great" | "Good" | "Fair" | "Poor";
  reason: string;
};

const clamp = (n: number) => Math.max(0, Math.min(10, Math.round(n)));
const verdict = (s: number): Activity["verdict"] =>
  s >= 8 ? "Great" : s >= 6 ? "Good" : s >= 4 ? "Fair" : "Poor";

function window(f: Forecast, now: number, hours: number): Hour[] {
  const i = currentHourIndex(f.hourly, now);
  return f.hourly.slice(i, i + hours);
}

function daylightWindow(f: Forecast, now: number): Hour[] {
  const ti = todayIndex(f, now);
  // After sunset, look at tomorrow's daytime instead.
  const day = now > f.daily[ti].sunset - 3600 && f.daily[ti + 1] ? f.daily[ti + 1] : f.daily[ti];
  return f.hourly.filter(
    (h) => h.time >= Math.max(now - 3600, day.sunrise) && h.time <= day.sunset,
  );
}

const avg = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0);
const max = (xs: number[]) => (xs.length ? Math.max(...xs) : 0);
const min = (xs: number[]) => (xs.length ? Math.min(...xs) : 0);

function make(id: string, name: string, emoji: string, score: number, reason: string): Activity {
  const s = clamp(score);
  return { id, name, emoji, score: s, verdict: verdict(s), reason };
}

export function lifeIndices(f: Forecast, now: number, aqi: number | null): Activity[] {
  const day = daylightWindow(f, now);
  const soon = window(f, now, 6);
  const next48 = window(f, now, 48);
  const out: Activity[] = [];

  // Running / outdoor exercise: the best 2-hour slot in the next 12 hours.
  {
    const hours = window(f, now, 12);
    let best = { score: -1, hour: hours[0] };
    for (const h of hours) {
      let s = 10;
      if (h.feels < 5) s -= 4;
      else if (h.feels < 10) s -= 1.5;
      else if (h.feels > 30) s -= 5;
      else if (h.feels > 25) s -= 2.5;
      if (h.pop >= 60) s -= 4;
      else if (h.pop >= 30) s -= 2;
      if (h.wind > 35) s -= 3;
      else if (h.wind > 25) s -= 1.5;
      if (h.uv >= 8) s -= 2;
      if (h.humidity > 85 && h.temp > 20) s -= 1.5;
      if (aqi != null && aqi > 100) s -= 3;
      if (s > best.score) best = { score: s, hour: h };
    }
    const hh = new Date((best.hour.time + f.utcOffset) * 1000).getUTCHours();
    out.push(
      make("run", "Running", "🏃", best.score, `Best around ${String(hh).padStart(2, "0")}:00`),
    );
  }

  // Braai: dry, not too windy, pleasant.
  {
    const evening = f.hourly.filter((h) => h.time >= now && h.time < now + 8 * 3600);
    const pop = max(evening.map((h) => h.pop));
    const wind = max(evening.map((h) => h.wind));
    const t = avg(evening.map((h) => h.feels));
    let s = 10 - (pop >= 50 ? 6 : pop >= 25 ? 3 : 0) - (wind > 35 ? 4 : wind > 25 ? 2 : 0);
    if (t < 12) s -= 3;
    if (t > 33) s -= 2;
    const why =
      pop >= 25
        ? `${pop}% chance of rain`
        : wind > 25
          ? "Breezy"
          : t < 12
            ? "Chilly"
            : "Dry and calm";
    out.push(make("braai", "Braai", "🔥", s, why));
  }

  // Laundry: drying conditions in the daytime.
  {
    const pop = max(day.map((h) => h.pop));
    const hum = avg(day.map((h) => h.humidity));
    const sun = avg(day.map((h) => 100 - h.cloud));
    const wind = avg(day.map((h) => h.wind));
    let s = 4 + sun / 25 + (wind > 8 && wind < 35 ? 2 : 0) - (hum > 80 ? 2 : 0);
    if (pop >= 50) s = Math.min(s, 2);
    else if (pop >= 30) s -= 3;
    const why =
      pop >= 30
        ? "Rain may spoil it"
        : hum > 80
          ? "Humid: slow drying"
          : sun > 60
            ? "Sunny and dry"
            : "Fair drying";
    out.push(make("laundry", "Laundry", "👕", s, why));
  }

  // Beach: warm, sunny, not too windy.
  {
    const t = max(day.map((h) => h.temp));
    const wind = avg(day.map((h) => h.wind));
    const cloud = avg(day.map((h) => h.cloud));
    const pop = max(day.map((h) => h.pop));
    let s = 10;
    if (t < 20) s -= 5;
    else if (t < 24) s -= 2;
    if (wind > 30) s -= 4;
    else if (wind > 20) s -= 2;
    if (cloud > 70) s -= 2;
    if (pop >= 40) s -= 3;
    const why =
      wind > 20
        ? "Windy on the sand"
        : t < 22
          ? "On the cool side"
          : cloud > 70
            ? "Mostly cloudy"
            : "Warm and sunny";
    out.push(make("beach", "Beach", "🏖️", s, why));
  }

  // Stargazing: tonight's cloud cover and the moon.
  {
    const ti = todayIndex(f, now);
    const sunset = f.daily[ti].sunset;
    const night = f.hourly.filter(
      (h) => h.time >= Math.max(now, sunset + 3600) && h.time < sunset + 7 * 3600,
    );
    const cloud = night.length ? avg(night.map((h) => h.cloud)) : 100;
    const moon = moonInfo(sunset + 4 * 3600).fraction;
    const s = 10 - cloud / 10 - moon * 4;
    const why = cloud > 60 ? "Cloudy tonight" : moon > 0.6 ? "Bright moon" : "Clear, dark skies";
    out.push(make("stars", "Stargazing", "🔭", s, why));
  }

  // Car wash: dry for two days.
  {
    const wetHours = next48.filter((h) => h.pop >= 40 || isWet(h.code)).length;
    const s = 10 - wetHours * 0.8;
    const why =
      wetHours === 0 ? "Dry for 2 days" : wetHours > 6 ? "Rain on the way" : "Some rain possible";
    out.push(make("carwash", "Car wash", "🚗", s, why));
  }

  // Driving: visibility, rain, wind.
  {
    const vis = min(soon.map((h) => h.visibility));
    const rain = max(soon.map((h) => h.precip));
    const gust = max(soon.map((h) => h.gust));
    let s = 10;
    if (vis < 1000) s -= 6;
    else if (vis < 5000) s -= 3;
    if (rain > 4) s -= 4;
    else if (rain > 1) s -= 2;
    if (gust > 70) s -= 3;
    else if (gust > 50) s -= 1.5;
    if (soon.some((h) => h.code >= 95)) s -= 3;
    const why =
      vis < 5000
        ? "Reduced visibility"
        : rain > 1
          ? "Wet roads"
          : gust > 50
            ? "Strong gusts"
            : "Good conditions";
    out.push(make("drive", "Driving", "🛣️", s, why));
  }

  // Garden watering: needed when it's warm and dry.
  {
    const rain = next48.reduce((a, h) => a + h.precip, 0);
    const t = max(day.map((h) => h.temp));
    const s = rain >= 5 ? 1 : 4 + (t > 25 ? 4 : t > 18 ? 2 : 0) + (rain < 1 ? 2 : 0);
    const why =
      rain >= 5
        ? `${Math.round(rain)} mm of rain coming`
        : t > 25
          ? "Hot and dry: water early"
          : "Light watering";
    out.push(make("garden", "Watering", "🌱", s, why));
  }

  return out;
}
