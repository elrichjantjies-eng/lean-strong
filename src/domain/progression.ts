/** 5.3 Prefill, progression suggestions and personal bests. */
import type { Exercise, Program } from "@/content/types";
import type { SessionLog, SetLog } from "./model";

/** Estimated 1-rep max (Epley): weight × (1 + reps / 30). */
export function epley(weightKg: number, reps: number): number {
  return weightKg * (1 + reps / 30);
}

const working = (sets: SetLog[]) => sets.filter((s) => !s.isWarmup);

export interface LastPerformance {
  log: SessionLog;
  /** Working sets of this exercise in that log, in set order. */
  sets: SetLog[];
}

/**
 * The most recent completed log of `exerciseId`, in any session (main pairs only: finisher and
 * circuit sets don't drive progression).
 */
export function lastPerformance(
  exerciseId: string,
  sessionLogs: SessionLog[],
  setLogs: SetLog[],
  excludeSessionLogId?: string,
): LastPerformance | null {
  const byLog = new Map<string, SetLog[]>();
  for (const s of setLogs) {
    if (s.exerciseId !== exerciseId || s.isWarmup || s.blockType !== "pair") continue;
    const list = byLog.get(s.sessionLogId) ?? [];
    list.push(s);
    byLog.set(s.sessionLogId, list);
  }
  const candidates = sessionLogs
    .filter((l) => l.status === "completed" && l.id !== excludeSessionLogId && byLog.has(l.id))
    .sort((a, b) => (b.finishedAt ?? b.startedAt) - (a.finishedAt ?? a.startedAt));
  const log = candidates[0];
  if (!log) return null;
  const sets = byLog.get(log.id)!.sort((a, b) => a.setIndex - b.setIndex);
  return { log, sets };
}

/** Prefill set `setIndex` from the matching set last time (or the last set if there were fewer). */
export function prefillSet(
  last: LastPerformance | null,
  setIndex: number,
): Pick<SetLog, "weightKg" | "reps" | "seconds"> {
  if (!last || last.sets.length === 0) return {};
  const match = last.sets.find((s) => s.setIndex === setIndex) ?? last.sets[last.sets.length - 1]!;
  return {
    ...(match.weightKg !== undefined ? { weightKg: match.weightKg } : {}),
    ...(match.reps !== undefined ? { reps: match.reps } : {}),
    ...(match.seconds !== undefined ? { seconds: match.seconds } : {}),
  };
}

export type Suggestion =
  | { kind: "first-time"; text: string }
  | { kind: "go-up"; incrementKg: number; note?: string }
  | { kind: "same-weight" }
  | { kind: "make-harder"; options: string[] }
  | { kind: "one-more-rep" }
  | { kind: "add-time"; addSecMin: number; addSecMax: number }
  | { kind: "hold-longer" }
  | null;

export const FIRST_TIME_GUIDANCE: Record<Program["setting"], string> = {
  gym: "Use about 60% of what you think you can lift",
  home: "Go easy and learn the movement",
};

/** Did every planned set reach the top of the range (reps or seconds)? */
export function hitTopOfRange(
  sets: SetLog[],
  plannedSets: number,
  max: number,
  by: "reps" | "seconds",
): boolean {
  const done = working(sets);
  return done.length >= plannedSets && plannedSets > 0 && done.every((s) => (s[by] ?? 0) >= max);
}

/** All working sets at the same, recorded weight? Returns that weight or null. */
export function sameWeight(sets: SetLog[]): number | null {
  const weights = working(sets).map((s) => s.weightKg);
  const first = weights[0];
  if (first === undefined || weights.some((w) => w !== first)) return null;
  return first;
}

/**
 * Suggestion before set 1, from the last completed log of this exercise. `plannedSets` and `max`
 * are the plan that applied in that log (its phase).
 */
export function suggestProgression(
  exercise: Exercise,
  setting: Program["setting"],
  last: { sets: SetLog[]; plannedSets: number; max: number } | null,
): Suggestion {
  if (!last || last.sets.length === 0) {
    return exercise.logAs === "none"
      ? null
      : { kind: "first-time", text: FIRST_TIME_GUIDANCE[setting] };
  }
  const prog = exercise.progression;
  switch (prog.type) {
    case "load": {
      const top =
        hitTopOfRange(last.sets, last.plannedSets, last.max, "reps") &&
        sameWeight(last.sets) !== null;
      return top
        ? {
            kind: "go-up",
            incrementKg: prog.incrementKg,
            ...(prog.note ? { note: prog.note } : {}),
          }
        : { kind: "same-weight" };
    }
    case "reps-then-harder":
      return hitTopOfRange(last.sets, last.plannedSets, last.max, "reps")
        ? { kind: "make-harder", options: prog.options }
        : { kind: "one-more-rep" };
    case "time":
      return hitTopOfRange(last.sets, last.plannedSets, last.max, "seconds")
        ? { kind: "add-time", addSecMin: prog.addSecMin, addSecMax: prog.addSecMax }
        : { kind: "hold-longer" };
    case "none":
      return null;
  }
}

/** Display text for a suggestion (uses the content's `**bold**` convention). */
export function suggestionText(s: NonNullable<Suggestion>): string {
  switch (s.kind) {
    case "first-time":
      return s.text;
    case "go-up":
      return `Go up **+${s.incrementKg} kg** today${s.note ? ` · ${s.note}` : ""}`;
    case "same-weight":
      return "Same weight. Aim for one more rep than last time.";
    case "make-harder":
      return "You've hit the top of the range. Make it harder:";
    case "one-more-rep":
      return "Aim for one more rep than last time.";
    case "add-time":
      return s.addSecMin === s.addSecMax
        ? `Add ${s.addSecMin} s`
        : `Add ${s.addSecMin}–${s.addSecMax} s`;
    case "hold-longer":
      return "Aim to hold a little longer than last time.";
  }
}

/** home-9wk: the moves where 8 kg dumbbells are the next step (from the brief, 5.3). */
export const DUMBBELL_UPGRADE_EXERCISES = [
  "db_bent_over_row",
  "db_floor_press",
  "db_reverse_lunge",
  "db_shoulder_press",
];

/** First time one of those moves hits the top of the range at 5 kg → suggest 8 kg dumbbells. */
export function shouldShowDumbbellTip(
  programId: string,
  exerciseId: string,
  last: { sets: SetLog[]; plannedSets: number; max: number } | null,
  alreadyShown: boolean,
): boolean {
  if (alreadyShown || programId !== "home-9wk" || !last) return false;
  if (!DUMBBELL_UPGRADE_EXERCISES.includes(exerciseId)) return false;
  return (
    hitTopOfRange(last.sets, last.plannedSets, last.max, "reps") && sameWeight(last.sets) === 5
  );
}

export interface Bests {
  e1rm: number | null;
  heaviest: number | null;
  /** Most reps at each weight (key: weight in kg, "0" for bodyweight). */
  repsAtWeight: Record<string, number>;
}

export function computeBests(sets: SetLog[]): Bests {
  const bests: Bests = { e1rm: null, heaviest: null, repsAtWeight: {} };
  for (const s of working(sets)) {
    if (s.reps === undefined) continue;
    if (s.weightKg !== undefined && s.weightKg > 0) {
      const e = epley(s.weightKg, s.reps);
      if (bests.e1rm === null || e > bests.e1rm) bests.e1rm = e;
      if (bests.heaviest === null || s.weightKg > bests.heaviest) bests.heaviest = s.weightKg;
    }
    const key = String(s.weightKg ?? 0);
    bests.repsAtWeight[key] = Math.max(bests.repsAtWeight[key] ?? 0, s.reps);
  }
  return bests;
}

export type NewBest =
  | { kind: "e1rm"; value: number }
  | { kind: "heaviest"; value: number }
  | { kind: "reps"; weightKg: number; reps: number };

/**
 * New personal bests set in `session` compared with everything `before` it, for one exercise.
 * A first-ever log isn't celebrated as a "best" (there's nothing to beat yet).
 */
export function newBests(before: SetLog[], session: SetLog[]): NewBest[] {
  if (working(before).length === 0) return [];
  const old = computeBests(before);
  const now = computeBests(session);
  const out: NewBest[] = [];
  if (now.e1rm !== null && old.e1rm !== null && now.e1rm > old.e1rm + 1e-9) {
    out.push({ kind: "e1rm", value: now.e1rm });
  }
  if (now.heaviest !== null && old.heaviest !== null && now.heaviest > old.heaviest) {
    out.push({ kind: "heaviest", value: now.heaviest });
  }
  for (const [w, reps] of Object.entries(now.repsAtWeight)) {
    const prev = old.repsAtWeight[w];
    if (prev !== undefined && reps > prev) out.push({ kind: "reps", weightKg: Number(w), reps });
  }
  return out;
}

/** Best set by estimated 1RM (or most reps when unweighted), e.g. for strength check-ins. */
export function bestSet(sets: SetLog[]): SetLog | null {
  let best: SetLog | null = null;
  let score = -1;
  for (const s of working(sets)) {
    if (s.reps === undefined) continue;
    const v = s.weightKg ? epley(s.weightKg, s.reps) : s.reps;
    if (v > score) {
      score = v;
      best = s;
    }
  }
  return best;
}
