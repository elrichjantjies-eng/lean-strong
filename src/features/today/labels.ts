import type { DayPlan, Program } from "@/content/types";
import type { WalkPlan } from "@/domain/session";

/** Colour for a day: its session's colour, or the walk/rest colour. */
export function dayColor(program: Program, day: DayPlan): string {
  if (day.kind === "workout" && day.sessionId) {
    return (
      program.theme.dayColors[day.sessionId] ??
      program.sessions[day.sessionId]?.color ??
      program.theme.accent
    );
  }
  return program.theme.dayColors[day.kind] ?? program.theme.muted;
}

/** One line describing walking intervals, built from `walkingIntervals`. */
export function walkIntervalsText(walk: WalkPlan): string | null {
  const iv = walk.intervals;
  if (!iv) return null;
  const [r1, r2] = iv.roundsRange;
  return `${iv.warmupMin} min easy, then ${r1}–${r2} rounds of ${iv.briskSec / 60} min brisk + ${iv.fastSec / 60} min fast, then ${iv.cooldownMin} min easy.`;
}
