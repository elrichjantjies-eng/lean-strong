import { useLiveQuery } from "dexie-react-hooks";
import type { Exercise, Program } from "@/content/types";
import { db } from "@/db/db";
import type { SessionLog, SetLog } from "@/domain/model";
import { lastPerformance, type LastPerformance } from "@/domain/progression";
import { resolveSession } from "@/domain/session";

/** All set logs for an exercise plus their session logs (completed ones only matter). */
export async function exerciseHistory(profileId: string, exerciseId: string) {
  const sets = await db.setLogs.where({ profileId, exerciseId }).toArray();
  const ids = [...new Set(sets.map((s) => s.sessionLogId))];
  const logs = (await db.sessionLogs.bulkGet(ids)).filter((l): l is SessionLog => !!l);
  return { sets, logs };
}

/** Last completed performance of an exercise (excluding the current session). */
export function useLastPerformance(
  profileId: string,
  exerciseId: string,
  excludeSessionLogId?: string,
): LastPerformance | null | undefined {
  return useLiveQuery(async () => {
    const { sets, logs } = await exerciseHistory(profileId, exerciseId);
    return lastPerformance(exerciseId, logs, sets, excludeSessionLogId);
  }, [profileId, exerciseId, excludeSessionLogId]);
}

/** What was planned for this exercise in that log (its session and week), for progression. */
export function plannedFor(
  program: Program,
  log: SessionLog,
  exerciseId: string,
  fallback: { sets: number; max: number },
): { plannedSets: number; max: number } {
  if (!program.sessions[log.sessionId]) return { plannedSets: fallback.sets, max: fallback.max };
  const resolved = resolveSession(program, log.sessionId, log.week);
  for (const pair of resolved.pairs) {
    for (const item of pair.items) {
      if (item.exerciseId === exerciseId) return { plannedSets: item.sets, max: item.target.max };
    }
  }
  return { plannedSets: fallback.sets, max: fallback.max };
}

const fmt = (n: number) => String(Number(n.toFixed(2)));

/** "60 kg × 10, 10, 9", "62.5 × 8, 60 × 10", "12, 12, 10 reps" or "40 s, 45 s". */
export function setsText(sets: SetLog[], exercise: Exercise): string {
  if (sets.length === 0) return "";
  if (exercise.logAs === "time") return sets.map((s) => `${s.seconds ?? 0} s`).join(", ");
  const weights = sets.map((s) => s.weightKg);
  if (weights.every((w) => w === undefined || w === 0)) {
    return `${sets.map((s) => s.reps ?? 0).join(", ")} reps`;
  }
  if (weights.every((w) => w === weights[0])) {
    return `${fmt(weights[0]!)} kg × ${sets.map((s) => s.reps ?? 0).join(", ")}`;
  }
  return sets.map((s) => `${fmt(s.weightKg ?? 0)} × ${s.reps ?? 0}`).join(", ") + " (kg × reps)";
}

/** Weight step for an exercise's stepper, from the user's default increments. */
export function weightStep(exercise: Exercise, barbellStep: number, dumbbellStep: number): number {
  switch (exercise.load) {
    case "barbell":
    case "machine":
    case "cable":
      return barbellStep;
    case "dumbbell":
    case "kettlebell":
      return dumbbellStep;
    default:
      return 0.5;
  }
}
