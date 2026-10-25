/** 5.6 Steps and daily numbers. */
import type { MinMax, Program } from "@/content/types";
import { programWeek } from "./schedule";
import type { DailyNumbers, IsoDate } from "./model";

type DailyField = "steps" | "waterL" | "calories" | "proteinG";

/** Average of one daily field over the days in program week `week` that have it logged. */
export function weeklyDailyAverage(
  entries: DailyNumbers[],
  startDate: IsoDate,
  week: number,
  field: DailyField,
): number | null {
  const values = entries
    .filter((e) => programWeek(startDate, e.date) === week)
    .map((e) => e[field])
    .filter((v): v is number => v !== undefined);
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
}

export function stepTarget(program: Program, week: number): number | null {
  return program.tracking.steps.dailyTargetByWeek[week - 1] ?? null;
}

export function stepsStatus(average: number | null, target: number | null) {
  if (average === null || target === null) return null;
  return { average, target, met: average >= target, ratio: average / target };
}

export function proteinStatus(grams: number, [min, max]: MinMax): "below" | "in-range" | "above" {
  if (grams < min) return "below";
  return grams > max ? "above" : "in-range";
}
