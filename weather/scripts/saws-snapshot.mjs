/**
 * Snapshot of the South African Weather Service's public data for the web app.
 *
 *   node scripts/saws-snapshot.mjs <outDir>      (default: public/saws)
 *
 * SAWS doesn't allow browsers to read its API from other sites (no CORS headers), so the iPhone
 * web app can't call it directly. The GitHub Pages workflow runs this every two hours and
 * publishes the files next to the app:
 *   forecast/<synopNo>.json   7-day official forecast per station
 *   alerts.json               warnings and thunderstorm cells
 *   cities.json               today's forecast for the main towns (map)
 * The Android app talks to SAWS live and only falls back to these.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseCities, parseDays, parseStorms, parseWarnings } from "../src/data/sawsParse.ts";

const API = "https://www.weathersa.co.za/api";
const out = process.argv[2] ?? "public/saws";

async function get(path) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const res = await fetch(`${API}/${path}`, { headers: { Accept: "application/json" } });
      const text = await res.text();
      if (res.status === 400 || res.status === 404) return [];
      if (!res.ok) throw new Error(`${res.status}`);
      return JSON.parse(text);
    } catch (err) {
      console.warn(`SAWS ${path}: attempt ${attempt} failed (${err.message})`);
      await new Promise((r) => setTimeout(r, attempt * 3000));
    }
  }
  return null;
}

const fetchedAt = Date.now();
mkdirSync(join(out, "forecast"), { recursive: true });

const rows = await get("DailyForecast?limit=1");
if (Array.isArray(rows)) {
  const bySynop = new Map();
  for (const r of rows) {
    if (!r.synopNo) continue;
    if (!bySynop.has(r.synopNo)) bySynop.set(r.synopNo, []);
    bySynop.get(r.synopNo).push(r);
  }
  let n = 0;
  for (const [synop, list] of bySynop) {
    const days = parseDays(list);
    if (days.length === 0) continue;
    writeFileSync(join(out, "forecast", `${synop}.json`), JSON.stringify({ fetchedAt, days }));
    n++;
  }
  console.log(`forecasts: ${n} stations`);
} else {
  console.warn("forecasts: unavailable, skipped");
}

const [warnings, storms, cities] = await Promise.all([
  get("WarningAlerts"),
  get("StormTrack"),
  get("CurrentWeather"),
]);
writeFileSync(
  join(out, "alerts.json"),
  JSON.stringify({
    fetchedAt,
    warnings: parseWarnings(warnings ?? []),
    storms: parseStorms(storms ?? []),
  }),
);
writeFileSync(
  join(out, "cities.json"),
  JSON.stringify({ fetchedAt, cities: parseCities(cities ?? []) }),
);
console.log(
  `warnings: ${parseWarnings(warnings ?? []).length}, storm cells: ${parseStorms(storms ?? []).length}, cities: ${parseCities(cities ?? []).length}`,
);
