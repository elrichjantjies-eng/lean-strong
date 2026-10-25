/** Data for the Progress screens, kept pure so the charts can be checked in tests. */
import type { Program } from "@/content/types";
import { computeCheckin, type CheckinResult } from "./coaching";
import { daysBetween } from "./dates";
import type {
  DailyNumbers,
  IsoDate,
  MeasurementEntry,
  SessionLog,
  SetLog,
  WeightEntry,
} from "./model";
import { bestSet } from "./progression";
import { programWeek } from "./schedule";
import { onTrackBand, targetKg, weeklyAverages, weightPlan } from "./weightPlan";

export type PaceStatus = "ahead" | "on-track" | "behind";

export interface WeightRow {
  week: number;
  target: number | null;
  band: [number, number] | null;
  average: number | null;
  /** average − target (negative = below target, i.e. ahead). */
  diff: number | null;
}

export interface WeightChartData {
  /** x in program weeks: a day d after the start sits at d / 7. */
  daily: { x: number; y: number }[];
  /** Weekly averages plotted mid-week (x = w − 0.5). */
  weekly: { x: number; y: number | null }[];
  /** Target path at the end of each week (x = w), from the start (x = 0). Empty when gaining. */
  target: { x: number; y: number }[];
  band: { x: number; lo: number; hi: number }[];
  goalKg: number;
  checkpoint: { x: number; y: number } | null;
  rows: WeightRow[];
  lostSoFar: number | null;
  status: PaceStatus | null;
  latestWeek: number | null;
}

export function weightChartData(
  program: Program,
  profile: { startDate: IsoDate; startKg: number; goalKg: number },
  entries: WeightEntry[],
): WeightChartData {
  const plan = weightPlan(program, profile.startKg, profile.goalKg);
  const averages = weeklyAverages(entries, profile.startDate, program.weeks);
  const daily = entries
    .map((e) => ({ x: daysBetween(profile.startDate, e.date) / 7, y: e.kg }))
    .filter((p) => p.x >= 0 && p.x < program.weeks)
    .sort((a, b) => a.x - b.x);
  const weekly = averages.map((y, i) => ({ x: i + 0.5, y }));
  const weeks = Array.from({ length: program.weeks + 1 }, (_, w) => w);
  const target = plan ? weeks.map((w) => ({ x: w, y: targetKg(plan, w) })) : [];
  const band = plan
    ? weeks.map((w) => {
        const [lo, hi] = onTrackBand(program, profile.startKg, w);
        return { x: w, lo, hi };
      })
    : [];
  const rows: WeightRow[] = averages.map((average, i) => {
    const week = i + 1;
    const t = plan ? targetKg(plan, week) : null;
    return {
      week,
      target: t,
      band: plan ? onTrackBand(program, profile.startKg, week) : null,
      average,
      diff: average !== null && t !== null ? average - t : null,
    };
  });

  const latest = [...rows].reverse().find((r) => r.average !== null) ?? null;
  let status: PaceStatus | null = null;
  if (plan && latest?.band && latest.average !== null) {
    const [lo, hi] = latest.band;
    status = latest.average < lo ? "ahead" : latest.average > hi ? "behind" : "on-track";
  }
  return {
    daily,
    weekly,
    target,
    band,
    goalKg: profile.goalKg,
    checkpoint:
      plan?.checkpointWeek != null && plan.checkpointKg != null
        ? { x: plan.checkpointWeek, y: plan.checkpointKg }
        : null,
    rows,
    lostSoFar: latest?.average != null ? profile.startKg - latest.average : null,
    status,
    latestWeek: latest?.week ?? null,
  };
}

/** Average logged calories over program weeks a..b (the check-in window). */
export function avgCalories(
  daily: DailyNumbers[],
  startDate: IsoDate,
  a: number,
  b: number,
): number | null {
  const values = daily
    .filter((d) => {
      const w = programWeek(startDate, d.date);
      return w !== null && w >= a && w <= b && d.calories !== undefined;
    })
    .map((d) => d.calories!);
  return values.length ? values.reduce((s, v) => s + v, 0) / values.length : null;
}

/** The week-`week` check-in from raw weight and daily logs. */
export function checkinFor(
  program: Program,
  startDate: IsoDate,
  week: number,
  weights: WeightEntry[],
  daily: DailyNumbers[],
): CheckinResult {
  const averages = weeklyAverages(weights, startDate, program.weeks);
  return computeCheckin(
    program,
    week,
    averages,
    avgCalories(daily, startDate, Math.max(1, week - 3), week - 1),
  );
}

/** Change in a measurement field from the first to the latest entry that has it. */
export function measurementTrend(entries: MeasurementEntry[], fieldId: string) {
  const withField = entries
    .filter((e) => e.values[fieldId] !== undefined)
    .sort((a, b) => (a.date < b.date ? -1 : 1));
  const first = withField[0];
  const last = withField[withField.length - 1];
  if (!first || !last) return null;
  const prev = withField[withField.length - 2];
  return {
    first: first.values[fieldId]!,
    latest: last.values[fieldId]!,
    change: last.values[fieldId]! - first.values[fieldId]!,
    sincePrevious: prev ? last.values[fieldId]! - prev.values[fieldId]! : null,
    points: withField.map((e) => ({ date: e.date, week: e.week, value: e.values[fieldId]! })),
  };
}

/** The field that tracks the waist (first field whose id starts with "waist"). */
export function waistFieldId(program: Program): string | null {
  return program.tracking.measurements.fields.find((f) => f.id.startsWith("waist"))?.id ?? null;
}

/** Best set per strength check-in exercise in each listed week, from completed logs. */
export function strengthCheckins(
  program: Program,
  sessionLogs: SessionLog[],
  setLogs: SetLog[],
): { exerciseId: string; byWeek: (SetLog | null)[] }[] {
  const sc = program.tracking.strengthCheckins;
  if (!sc) return [];
  const weekOf = new Map(
    sessionLogs.filter((l) => l.status === "completed").map((l) => [l.id, l.week]),
  );
  return sc.exerciseIds.map((exerciseId) => ({
    exerciseId,
    byWeek: sc.weeks.map((w) =>
      bestSet(
        setLogs.filter(
          (s) =>
            s.exerciseId === exerciseId &&
            s.blockType === "pair" &&
            weekOf.get(s.sessionLogId) === w,
        ),
      ),
    ),
  }));
}
