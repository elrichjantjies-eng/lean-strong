import { useState } from "react";
import { Link, useParams } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive } from "@/app/active";
import type { Program } from "@/content/types";
import { bestSet, computeBests, epley } from "@/domain/progression";
import { ExerciseCues, ExerciseDemo, ExerciseFrames } from "@/components/ExerciseMedia";
import { LineChart } from "@/components/charts";
import { Screen } from "@/components/Screen";
import { Card } from "@/components/ui";
import { exerciseHistory, setsText } from "@/features/player/history";

/** "Day A · A1", "Workout A · Waist circuit", … */
function usedIn(program: Program, exerciseId: string): string[] {
  const out: string[] = [];
  for (const s of Object.values(program.sessions)) {
    for (const pair of s.pairs) {
      for (const item of pair.items)
        if (item.exerciseId === exerciseId) out.push(`${s.title} · ${item.code}`);
    }
    if (
      s.finisher?.kind === "circuit" &&
      s.finisher.circuit.steps.some((st) => st.exerciseId === exerciseId)
    ) {
      out.push(`${s.title} · Finisher`);
    }
    if (s.waistCircuit?.steps.some((st) => st.exerciseId === exerciseId)) {
      out.push(`${s.title} · ${s.waistCircuit.title}`);
    }
  }
  return out;
}

export function ExerciseDetailScreen() {
  const { exerciseId = "" } = useParams();
  const { profile, program } = useActive();
  const exercise = program.exercises[exerciseId];
  const [steps, setSteps] = useState(false);
  const history = useLiveQuery(async () => {
    const { sets, logs } = await exerciseHistory(profile.id, exerciseId);
    const done = logs
      .filter((l) => l.status === "completed")
      .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.startedAt - b.startedAt));
    const sessions = done
      .map((log) => ({
        log,
        sets: sets
          .filter((s) => s.sessionLogId === log.id && s.blockType === "pair")
          .sort((a, b) => a.setIndex - b.setIndex),
      }))
      .filter((x) => x.sets.length > 0);
    const doneIds = new Set(done.map((l) => l.id));
    return {
      sessions,
      all: sets.filter((s) => doneIds.has(s.sessionLogId) && s.blockType === "pair"),
    };
  }, [profile.id, exerciseId]);

  if (!exercise) {
    return (
      <Screen title="Exercise" back="/exercises">
        <p className="text-muted">That exercise isn't in your program.</p>
      </Screen>
    );
  }
  const variants = exercise.framesAreVariants;
  const showFrames = steps || variants || !exercise.images.loop;
  const where = usedIn(program, exercise.id);
  const bests = history ? computeBests(history.all) : null;
  const weighted = exercise.logAs === "weight-reps";
  const points =
    history?.sessions.map((s, i) => {
      const b = bestSet(s.sets);
      const y =
        exercise.logAs === "time"
          ? Math.max(...s.sets.map((x) => x.seconds ?? 0))
          : b
            ? weighted && b.weightKg
              ? epley(b.weightKg, b.reps ?? 0)
              : (b.reps ?? 0)
            : null;
      return { x: i + 1, y };
    }) ?? [];

  return (
    <Screen title={exercise.name} kicker={exercise.muscles.join(" · ")} back="/exercises">
      <div className="space-y-5">
        <div>
          {showFrames ? (
            <ExerciseFrames exercise={exercise} />
          ) : (
            <ExerciseDemo exercise={exercise} className="h-72" />
          )}
          {!variants && exercise.images.loop && (
            <div
              className="mt-2 grid grid-cols-2 rounded-xl bg-surface-2 p-1"
              role="group"
              aria-label="View"
            >
              {[
                ["Animation", false],
                ["Steps", true],
              ].map(([label, value]) => (
                <button
                  key={String(label)}
                  type="button"
                  aria-pressed={steps === value}
                  onClick={() => setSteps(value as boolean)}
                  className={`min-h-11 rounded-lg font-semibold ${steps === value ? "bg-surface text-ink shadow-sm" : "text-muted"}`}
                >
                  {label as string}
                </button>
              ))}
            </div>
          )}
        </div>

        <ExerciseCues exercise={exercise} />

        {where.length > 0 && (
          <Card>
            <p className="mb-1 text-sm font-bold tracking-wide text-muted uppercase">
              Where it's used
            </p>
            <ul className="flex flex-wrap gap-2">
              {where.map((w) => (
                <li
                  key={w}
                  className="rounded-full bg-surface-2 px-3 py-1 text-sm font-semibold text-ink"
                >
                  {w}
                </li>
              ))}
            </ul>
          </Card>
        )}

        <section aria-labelledby="history-heading">
          <h2
            id="history-heading"
            className="mb-2 text-sm font-bold tracking-wide text-muted uppercase"
          >
            My history
          </h2>
          {!history?.sessions.length ? (
            <p className="text-muted">No sessions yet. Your sets show up here once you log them.</p>
          ) : (
            <div className="space-y-4">
              {bests && (weighted || exercise.logAs === "reps") && (
                <div className="grid grid-cols-3 gap-2">
                  {weighted && (
                    <>
                      <Best
                        label="Best est. 1RM"
                        value={bests.e1rm ? `${Math.round(bests.e1rm)} kg` : "–"}
                      />
                      <Best
                        label="Heaviest"
                        value={bests.heaviest ? `${bests.heaviest} kg` : "–"}
                      />
                    </>
                  )}
                  <Best
                    label="Most reps"
                    value={String(Math.max(0, ...Object.values(bests.repsAtWeight)))}
                  />
                </div>
              )}
              {points.length > 1 && (
                <Card>
                  <LineChart
                    title={`${exercise.name}: best set per session`}
                    series={[
                      {
                        id: "best",
                        label:
                          exercise.logAs === "time"
                            ? "Longest hold (s)"
                            : weighted
                              ? "Best set (est. 1RM, kg)"
                              : "Most reps",
                        color: "var(--app-accent)",
                        points,
                        dots: true,
                      },
                    ]}
                    xLabel={(x) => `#${x}`}
                  />
                </Card>
              )}
              <Card className="py-1">
                <ul className="divide-y divide-line">
                  {[...history.sessions]
                    .reverse()
                    .slice(0, 8)
                    .map(({ log, sets }) => (
                      <li key={log.id} className="flex justify-between gap-3 py-2.5 text-sm">
                        <span className="text-muted">
                          {log.date} · {program.sessions[log.sessionId]?.title ?? log.sessionId}
                        </span>
                        <span className="text-right font-semibold text-ink">
                          {setsText(sets, exercise)}
                        </span>
                      </li>
                    ))}
                </ul>
              </Card>
            </div>
          )}
        </section>
        <Link to="/exercises" className="block text-center text-sm font-semibold text-accent-ink">
          All exercises
        </Link>
      </div>
    </Screen>
  );
}

function Best({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-surface p-3 text-center ring-1 ring-line">
      <p className="text-xs font-semibold text-muted">{label}</p>
      <p className="text-lg font-bold text-ink tabular-nums">{value}</p>
    </div>
  );
}
