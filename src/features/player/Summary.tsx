import { useState } from "react";
import { useNavigate } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import type { Program } from "@/content/types";
import { db } from "@/db/db";
import type { Profile, SetLog } from "@/domain/model";
import { newBests, type NewBest } from "@/domain/progression";
import { formatClock } from "@/features/timers/engine";
import { QuickAdd } from "@/features/today/QuickAdd";
import { Button, Card } from "@/components/ui";
import { exerciseHistory } from "./history";
import type { WorkoutSnapshot } from "./store";

const CHEERS = [
  "Another one in the bank. That's how it's done.",
  "Session done. Consistency beats everything.",
  "Strong work today. Recover well.",
  "You showed up and did the work. Well done.",
];

function bestText(program: Program, exerciseId: string, b: NewBest): string {
  const name = program.exercises[exerciseId]?.name ?? exerciseId;
  if (b.kind === "heaviest") return `${name}: heaviest ever, ${b.value} kg`;
  if (b.kind === "e1rm") return `${name}: best estimated 1-rep max, ${Math.round(b.value)} kg`;
  return `${name}: ${b.reps} reps at ${b.weightKg ? `${b.weightKg} kg` : "bodyweight"}`;
}

export function Summary({
  snap,
  program,
  profile,
  finishedAt,
}: {
  snap: WorkoutSnapshot;
  program: Program;
  profile: Profile;
  finishedAt: number;
}) {
  const navigate = useNavigate();
  const [cheer] = useState(() => CHEERS[Math.floor(Math.random() * CHEERS.length)]!);
  const data = useLiveQuery(async () => {
    const sets = await db.setLogs.where("sessionLogId").equals(snap.sessionLogId).toArray();
    const pairSets = sets.filter((s) => s.blockType === "pair");
    const bests: { exerciseId: string; best: NewBest }[] = [];
    for (const exerciseId of new Set(pairSets.map((s) => s.exerciseId))) {
      const { sets: all, logs } = await exerciseHistory(profile.id, exerciseId);
      const done = new Set(
        logs.filter((l) => l.status === "completed" && l.id !== snap.sessionLogId).map((l) => l.id),
      );
      const before = all.filter((s) => done.has(s.sessionLogId) && s.blockType === "pair");
      for (const best of newBests(
        before,
        pairSets.filter((s) => s.exerciseId === exerciseId),
      )) {
        bests.push({ exerciseId, best });
      }
    }
    return { sets, pairSets, bests };
  }, [snap.sessionLogId]);

  if (!data) return null;
  const volume = data.sets.reduce(
    (sum: number, s: SetLog) => sum + (s.weightKg ?? 0) * (s.reps ?? 0),
    0,
  );
  const seconds = Math.round((finishedAt - snap.startedAt) / 1000);
  const session = program.sessions[snap.sessionId];

  return (
    <div className="space-y-5">
      <div className="pt-4 text-center">
        <p className="text-5xl" aria-hidden="true">
          🎉
        </p>
        <h2 className="mt-2 text-3xl font-bold text-ink">{session?.title} complete</h2>
        <p className="mt-1 text-muted">
          {data.bests.length ? "New personal best. That's what progress looks like." : cheer}
        </p>
      </div>
      <div className="grid grid-cols-3 gap-2">
        <Stat label="Time" value={formatClock(seconds)} />
        <Stat label="Sets" value={String(data.pairSets.length)} />
        <Stat label="Volume" value={`${Math.round(volume).toLocaleString()} kg`} />
      </div>
      {data.bests.length > 0 && (
        <Card>
          <h3 className="mb-2 font-bold text-ink">New personal bests</h3>
          <ul className="space-y-1">
            {data.bests.map(({ exerciseId, best }, i) => (
              <li key={i} className="text-ink">
                🏆 {bestText(program, exerciseId, best)}
              </li>
            ))}
          </ul>
        </Card>
      )}
      <section>
        <h3 className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">Log today</h3>
        <QuickAdd program={program} profile={profile} today={snap.date} />
      </section>
      <Button size="lg" className="w-full" onClick={() => navigate("/", { replace: true })}>
        Back to Today
      </Button>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-surface p-3 text-center shadow-sm ring-1 ring-line">
      <p className="text-xs font-semibold text-muted uppercase">{label}</p>
      <p className="text-lg font-bold text-ink tabular-nums">{value}</p>
    </div>
  );
}
