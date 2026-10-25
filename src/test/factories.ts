import type { SessionLog, SetLog } from "@/domain/model";

let n = 0;
const id = () => `id-${++n}`;

export function sessionLog(over: Partial<SessionLog> = {}): SessionLog {
  return {
    id: id(),
    profileId: "p1",
    programId: "gym-12wk",
    sessionId: "A",
    week: 1,
    phase: 1,
    date: "2026-11-02",
    startedAt: 1_000,
    finishedAt: 2_000,
    status: "completed",
    isExtra: false,
    notes: "",
    ...over,
  };
}

export function setLog(over: Partial<SetLog> = {}): SetLog {
  return {
    id: id(),
    sessionLogId: "s1",
    profileId: "p1",
    exerciseId: "bench_press",
    blockType: "pair",
    pairCode: "A1",
    setIndex: 0,
    perSide: false,
    isWarmup: false,
    completedAt: 1_500,
    ...over,
  };
}

/** Sets for one log: `[[kg, reps], …]`. */
export function sets(
  sessionLogId: string,
  rows: [number | undefined, number][],
  exerciseId = "bench_press",
) {
  return rows.map(([weightKg, reps], setIndex) =>
    setLog({
      sessionLogId,
      exerciseId,
      setIndex,
      reps,
      ...(weightKg !== undefined ? { weightKg } : {}),
    }),
  );
}
