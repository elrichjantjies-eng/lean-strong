/**
 * Forecast times are Unix seconds. Every place is shown in its own local time (a Cape Town
 * forecast viewed from London still says sunrise 05:32), so formatting adds the place's UTC
 * offset and then reads the UTC fields.
 */

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function local(t: number, offset: number): Date {
  return new Date((t + offset) * 1000);
}

export function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

export function fmtTime(t: number, offset: number, h24 = true): string {
  const d = local(t, offset);
  const h = d.getUTCHours();
  const m = d.getUTCMinutes();
  if (h24) return `${pad(h)}:${pad(m)}`;
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${pad(m)} ${h < 12 ? "AM" : "PM"}`;
}

/** "14" (24 h) or "2 PM" (12 h): the hour label used in the hourly strip. */
export function fmtHour(t: number, offset: number, h24 = true): string {
  const h = local(t, offset).getUTCHours();
  if (h24) return pad(h);
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12} ${h < 12 ? "AM" : "PM"}`;
}

export function weekday(t: number, offset: number, short = false): string {
  const w = WEEKDAYS[local(t, offset).getUTCDay()];
  return short ? w.slice(0, 3) : w;
}

export function fmtDate(t: number, offset: number): string {
  const d = local(t, offset);
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
}

/** YYYY-MM-DD in the place's local time. */
export function dayKey(t: number, offset: number): string {
  const d = local(t, offset);
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
}

export function hourOfDay(t: number, offset: number): number {
  const d = local(t, offset);
  return d.getUTCHours() + d.getUTCMinutes() / 60;
}

export function nowSec(): number {
  return Math.floor(Date.now() / 1000);
}

/** "3h 12m" */
export function fmtDuration(seconds: number): string {
  const m = Math.round(seconds / 60);
  const h = Math.floor(m / 60);
  return h > 0 ? `${h}h ${pad(m % 60)}m` : `${m}m`;
}

/** "just now", "5 min ago", "2 h ago" */
export function ago(ms: number, now = Date.now()): string {
  const s = Math.max(0, Math.round((now - ms) / 1000));
  if (s < 60) return "just now";
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.round(h / 24)} d ago`;
}
