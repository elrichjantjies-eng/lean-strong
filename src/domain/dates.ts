import type { Weekday } from "@/content/types";
import type { IsoDate } from "./model";

export const WEEKDAYS: Weekday[] = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];

const DAY_MS = 86_400_000;

function parts(date: IsoDate): [number, number, number] {
  const [y, m, d] = date.split("-").map(Number) as [number, number, number];
  return [y, m, d];
}

/** Days from `a` to `b` as calendar dates (independent of time zone and DST). */
export function daysBetween(a: IsoDate, b: IsoDate): number {
  const [ay, am, ad] = parts(a);
  const [by, bm, bd] = parts(b);
  return Math.round((Date.UTC(by, bm - 1, bd) - Date.UTC(ay, am - 1, ad)) / DAY_MS);
}

export function addDays(date: IsoDate, days: number): IsoDate {
  const [y, m, d] = parts(date);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** Local calendar date of a JS Date (defaults to now). */
export function toIsoDate(date: Date = new Date()): IsoDate {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** 0 = Monday … 6 = Sunday. */
export function weekdayIndex(date: IsoDate): number {
  const [y, m, d] = parts(date);
  return (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7;
}

export function weekdayOf(date: IsoDate): Weekday {
  return WEEKDAYS[weekdayIndex(date)]!;
}

/** Default start date: today if it's Monday, otherwise next Monday. */
export function nextMondayOrToday(today: IsoDate): IsoDate {
  const i = weekdayIndex(today);
  return i === 0 ? today : addDays(today, 7 - i);
}

const WEEKDAY_NAMES: Record<Weekday, string> = {
  mon: "Monday",
  tue: "Tuesday",
  wed: "Wednesday",
  thu: "Thursday",
  fri: "Friday",
  sat: "Saturday",
  sun: "Sunday",
};

export function weekdayName(day: Weekday): string {
  return WEEKDAY_NAMES[day];
}
