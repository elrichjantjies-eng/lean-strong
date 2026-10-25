/** 5.5 Weekly check-in coach. */
import type { CoachingBand, Program } from "@/content/types";

/** Check-ins run at the start of week w ≥ 3 when (w − 1) % reviewEveryWeeks === 0. */
export function isCheckinWeek(program: Program, week: number): boolean {
  return week >= 3 && (week - 1) % program.coaching.reviewEveryWeeks === 0;
}

/** The band with min ≤ rate < max (a missing bound is open). */
export function selectBand(bands: CoachingBand[], rate: number): CoachingBand | null {
  return (
    bands.find(
      (b) =>
        (b.minKgPerWeek === undefined || b.minKgPerWeek <= rate) &&
        (b.maxKgPerWeek === undefined || rate < b.maxKgPerWeek),
    ) ?? null
  );
}

export const UNLOCK_MESSAGE = "Log your weight for another week to unlock your check-in";

export type CheckinResult =
  | { status: "locked"; message: string }
  | {
      status: CoachingBand["status"];
      rate: number;
      fromWeek: number;
      toWeek: number;
      band: CoachingBand;
      message: string;
      calorieWarning: boolean;
    };

/**
 * Compare weekly averages: b = w − 1 (last finished week), a = max(1, w − 3);
 * rate = (avg[a] − avg[b]) / (b − a) kg lost per week. A week with no data is replaced by the
 * nearest week with data inside that window.
 *
 * @param averages weekly averages, index 0 = week 1
 * @param avgCalories average logged daily calories over the window, if any were logged
 */
export function computeCheckin(
  program: Program,
  week: number,
  averages: (number | null)[],
  avgCalories?: number | null,
): CheckinResult {
  const b0 = week - 1;
  const a0 = Math.max(1, week - 3);
  const withData: number[] = [];
  for (let w = a0; w <= b0; w++) if (averages[w - 1] != null) withData.push(w);

  if (withData.length < program.coaching.minWeeksOfData || withData.length < 2) {
    return { status: "locked", message: UNLOCK_MESSAGE };
  }
  const a = withData[0]!;
  const b = withData[withData.length - 1]!;
  const rate = (averages[a - 1]! - averages[b - 1]!) / (b - a);
  const band = selectBand(program.coaching.bands, rate);
  if (!band) return { status: "locked", message: UNLOCK_MESSAGE };
  const floor = program.weightPlan.calorieFloorKcal;
  return {
    status: band.status,
    rate,
    fromWeek: a,
    toWeek: b,
    band,
    message: band.message,
    calorieWarning: floor !== null && avgCalories != null && avgCalories < floor,
  };
}
