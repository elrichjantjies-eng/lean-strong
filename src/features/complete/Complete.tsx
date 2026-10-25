import { useState } from "react";
import { useNavigate } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import { programList } from "@/content/loader";
import { db, newId } from "@/db/db";
import { setSetting } from "@/db/settings";
import { nextMondayOrToday } from "@/domain/dates";
import type { Profile } from "@/domain/model";
import { bestSet, epley } from "@/domain/progression";
import { measurementTrend, waistFieldId } from "@/domain/progress";
import { weeklyAverages } from "@/domain/weightPlan";
import { Screen } from "@/components/Screen";
import { Stepper } from "@/components/Stepper";
import { Button, Card, Field, inputClass } from "@/components/ui";

const signed = (n: number, d = 1) => `${n > 0 ? "+" : ""}${n.toFixed(d)}`;

/** 4.11: a summary of the whole program, then start again or switch program. */
export function CompleteScreen() {
  const { profile, program } = useActive();
  const data = useLiveQuery(async () => {
    const [logs, sets, weights, measurements, wins, tests] = await Promise.all([
      db.sessionLogs.where("profileId").equals(profile.id).toArray(),
      db.setLogs.where("profileId").equals(profile.id).toArray(),
      db.weights.where("profileId").equals(profile.id).toArray(),
      db.measurements.where("profileId").equals(profile.id).toArray(),
      db.wins.where("profileId").equals(profile.id).toArray(),
      db.fitnessTests.where("profileId").equals(profile.id).toArray(),
    ]);
    return { logs, sets, weights, measurements, wins, tests };
  }, [profile.id]);
  if (!data) return null;

  const done = data.logs.filter((l) => l.status === "completed");
  const doneIds = new Set(done.map((l) => l.id));
  const averages = weeklyAverages(data.weights, profile.startDate, program.weeks);
  const lastAvg = [...averages].reverse().find((a) => a !== null) ?? null;
  const waistId = waistFieldId(program);
  const waist = waistId ? measurementTrend(data.measurements, waistId) : null;
  const winsDone = data.wins.filter((w) => w.achievedOn).length;

  // Strength: best estimated 1RM in the first vs the last week each lift was logged.
  const lifts = (program.tracking.strengthCheckins?.exerciseIds ?? [])
    .map((id) => {
      const byWeek = new Map<number, number>();
      for (const log of done) {
        const b = bestSet(
          data.sets.filter(
            (s) => s.sessionLogId === log.id && s.exerciseId === id && doneIds.has(s.sessionLogId),
          ),
        );
        if (b?.weightKg && b.reps)
          byWeek.set(log.week, Math.max(byWeek.get(log.week) ?? 0, epley(b.weightKg, b.reps)));
      }
      const weeks = [...byWeek.keys()].sort((a, b) => a - b);
      if (weeks.length < 2) return null;
      return { id, from: byWeek.get(weeks[0]!)!, to: byWeek.get(weeks[weeks.length - 1]!)! };
    })
    .filter((x): x is NonNullable<typeof x> => x !== null);

  // Fitness tests: first vs last week with a result.
  const tests = (program.tracking.fitnessTests?.tests ?? [])
    .map((t) => {
      const rows = data.tests.filter((r) => r.testId === t.id).sort((a, b) => a.week - b.week);
      return rows.length >= 2
        ? { t, from: rows[0]!.value, to: rows[rows.length - 1]!.value }
        : null;
    })
    .filter((x): x is NonNullable<typeof x> => x !== null);

  return (
    <Screen title="Program complete!" kicker={program.subtitle}>
      <div className="space-y-4">
        <Card className="text-center">
          <p className="text-5xl" aria-hidden="true">
            🏁
          </p>
          <p className="mt-2 text-lg text-ink">
            You finished all {program.weeks} weeks, {profile.name}. Be proud of that.
          </p>
        </Card>
        <div className="grid grid-cols-2 gap-2">
          <Stat
            label="Weight change"
            value={lastAvg === null ? "–" : `${signed(lastAvg - profile.startKg)} kg`}
          />
          <Stat label="Waist change" value={waist ? `${signed(waist.change)} cm` : "–"} />
          <Stat label="Sessions done" value={String(done.length)} />
          <Stat label="Wins" value={String(winsDone)} />
        </div>
        {lifts.length > 0 && (
          <Card>
            <p className="mb-2 font-bold text-ink">Stronger (estimated 1-rep max)</p>
            <ul className="space-y-1 text-sm">
              {lifts.map((l) => (
                <li key={l.id} className="flex justify-between">
                  <span className="text-ink">{program.exercises[l.id]?.name}</span>
                  <span className="font-semibold text-ink tabular-nums">
                    {Math.round(l.from)} → {Math.round(l.to)} kg
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        )}
        {tests.length > 0 && (
          <Card>
            <p className="mb-2 font-bold text-ink">Fitter</p>
            <ul className="space-y-1 text-sm">
              {tests.map(({ t, from, to }) => (
                <li key={t.id} className="flex justify-between gap-3">
                  <span className="text-ink">{t.label}</span>
                  <span className="font-semibold text-ink tabular-nums">
                    {from} → {to} {t.unit}
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        )}
        <NextRun profile={profile} lastKg={lastAvg} />
      </div>
    </Screen>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-surface p-3 text-center ring-1 ring-line">
      <p className="text-xs font-semibold text-muted uppercase">{label}</p>
      <p className="text-xl font-bold text-ink tabular-nums">{value}</p>
    </div>
  );
}

function NextRun({ profile, lastKg }: { profile: Profile; lastKg: number | null }) {
  const navigate = useNavigate();
  const today = useToday();
  const [programId, setProgramId] = useState(profile.programId);
  const [startDate, setStartDate] = useState(nextMondayOrToday(today));
  const [startKg, setStartKg] = useState<number | undefined>(
    lastKg ? Math.round(lastKg * 10) / 10 : profile.startKg,
  );
  const [goalKg, setGoalKg] = useState<number | undefined>(profile.goalKg);

  async function begin() {
    if (!startKg || !goalKg) return;
    const id = newId();
    await db.transaction("rw", db.profiles, async () => {
      await db.profiles.update(profile.id, { archived: true });
      await db.profiles.add({
        ...profile,
        id,
        programId,
        startDate,
        startKg,
        goalKg,
        createdAt: Date.now(),
        previousProfileId: profile.id,
        archived: false,
      });
    });
    await setSetting("activeProfileId", id);
    navigate("/", { replace: true });
  }

  return (
    <Card className="space-y-4">
      <p className="font-bold text-ink">What's next?</p>
      <p className="text-sm text-muted">
        Start again with new numbers, or switch program. This run stays on the phone, and your
        exercise history carries over.
      </p>
      <Field label="Program">
        <select
          className={inputClass}
          value={programId}
          onChange={(e) => setProgramId(e.target.value)}
        >
          {programList.map((p) => (
            <option key={p.id} value={p.id}>
              {p.subtitle}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Start date">
        <input
          type="date"
          className={inputClass}
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
        />
      </Field>
      <Stepper
        label="Weight now"
        unit="kg"
        step={0.1}
        decimals={1}
        min={30}
        max={300}
        value={startKg}
        onChange={setStartKg}
      />
      <Stepper
        label="New goal"
        unit="kg"
        step={0.5}
        decimals={1}
        min={30}
        max={300}
        value={goalKg}
        onChange={setGoalKg}
      />
      <Button
        size="lg"
        className="w-full"
        disabled={!startKg || !goalKg}
        onClick={() => void begin()}
      >
        {programId === profile.programId ? "Start again" : "Switch program"}
      </Button>
    </Card>
  );
}
