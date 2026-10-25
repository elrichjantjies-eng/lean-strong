/** 5.4 Weight plan, target path, on-track band and weekly averages. */
import type { Program } from "@/content/types";
import { programWeek } from "./schedule";
import type { IsoDate } from "./model";

export interface WeightPlan {
  startKg: number;
  goalKg: number;
  /** kg lost per week (exact; display with `toFixed(1)`). */
  ratePerWeek: number;
  weeksToGoal: number;
  /** True when the goal needs more than the healthy maximum pace. */
  capped: boolean;
  /** True when weeksToGoal is beyond the program length. */
  beyondProgram: boolean;
  checkpointWeek: number | null;
  checkpointKg: number | null;
}

/** Null when maintaining or gaining (goal ≥ start): no path, band or weight coaching. */
export function weightPlan(program: Program, startKg: number, goalKg: number): WeightPlan | null {
  if (goalKg >= startKg) return null;
  const toLose = startKg - goalKg;
  const wanted = toLose / program.weeks;
  const max = program.weightPlan.maxRateKgPerWeek;
  const ratePerWeek = Math.min(wanted, max);
  // The epsilon matters: 5 / (5/12) is 12.000000001 in floating point.
  const weeksToGoal = Math.ceil(toLose / ratePerWeek - 1e-9);
  const plan: WeightPlan = {
    startKg,
    goalKg,
    ratePerWeek,
    weeksToGoal,
    capped: wanted > max,
    beyondProgram: weeksToGoal > program.weeks,
    checkpointWeek: program.weightPlan.checkpointWeek,
    checkpointKg: null,
  };
  if (plan.checkpointWeek !== null) {
    plan.checkpointKg = Math.round(targetKg(plan, plan.checkpointWeek) * 2) / 2;
  }
  return plan;
}

/** Target for week `w`: `startKg − ratePerWeek × w`, never below the goal. */
export function targetKg(
  plan: Pick<WeightPlan, "startKg" | "goalKg" | "ratePerWeek">,
  week: number,
): number {
  return Math.max(plan.goalKg, plan.startKg - plan.ratePerWeek * week);
}

/** On-track band for week `w`: from `startKg − band[1]·w` (low) to `startKg − band[0]·w` (high). */
export function onTrackBand(program: Program, startKg: number, week: number): [number, number] {
  const [slow, fast] = program.weightPlan.onTrackBandKgPerWeek;
  return [startKg - fast * week, startKg - slow * week];
}

/**
 * Mean of all entries dated within each program week, indexed by week (index 0 = week 1).
 * A week with no entries stays null (no interpolation).
 */
export function weeklyAverages(
  entries: { date: IsoDate; kg: number }[],
  startDate: IsoDate,
  weeks: number,
): (number | null)[] {
  const sums = Array.from({ length: weeks }, () => ({ sum: 0, n: 0 }));
  for (const e of entries) {
    const w = programWeek(startDate, e.date);
    if (w === null || w > weeks) continue;
    const bucket = sums[w - 1]!;
    bucket.sum += e.kg;
    bucket.n += 1;
  }
  return sums.map((b) => (b.n ? b.sum / b.n : null));
}

/** Values for `{placeholders}` in program text. */
export function placeholderValues(program: Program, startKg: number, goalKg: number) {
  const plan = weightPlan(program, startKg, goalKg);
  return {
    startKg,
    goalKg,
    weeks: program.weeks,
    ...(plan
      ? {
          ratePerWeek: plan.ratePerWeek.toFixed(1),
          weeksToGoal: plan.weeksToGoal,
          ...(plan.checkpointWeek !== null
            ? { checkpointWeek: plan.checkpointWeek, checkpointKg: plan.checkpointKg ?? undefined }
            : {}),
        }
      : {}),
  };
}
