/**
 * Rebuilds src/data/saws-stations.json, the list of SAWS forecast stations used to find the
 * official forecast nearest to a place. SAWS's station search needs a name, so search by
 * every letter and merge.
 *
 *   node scripts/update-stations.mjs
 */
import { writeFileSync } from "node:fs";

const found = new Map();
for (const q of "abcdefghijklmnopqrstuvwxyz") {
  const res = await fetch(`https://www.weathersa.co.za/api/stations?stasName=${q}`);
  if (!res.ok) continue;
  for (const s of await res.json()) {
    const lat = Number.parseFloat(s.latitude);
    const lon = Number.parseFloat(s.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    found.set(s.synopNo, [
      s.synopNo,
      s.stationName.trim(),
      (s.province ?? "").trim(),
      (s.municipality ?? "").trim(),
      Math.round(lat * 1e4) / 1e4,
      Math.round(lon * 1e4) / 1e4,
    ]);
  }
}
const list = [...found.values()].sort((a, b) => a[1].localeCompare(b[1]));
writeFileSync("src/data/saws-stations.json", JSON.stringify(list));
console.log(`${list.length} stations`);
