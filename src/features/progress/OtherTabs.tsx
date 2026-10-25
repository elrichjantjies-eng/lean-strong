import { useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import type { FitnessTest } from "@/content/types";
import { db, newId } from "@/db/db";
import { logDaily } from "@/db/repo";
import { addDays, weekdayOf } from "@/domain/dates";
import { stepTarget, weeklyDailyAverage } from "@/domain/daily";
import { strengthCheckins } from "@/domain/progress";
import { programWeek } from "@/domain/schedule";
import { BarChart } from "@/components/charts";
import { RichText } from "@/components/RichText";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button, Card, Field, inputClass } from "@/components/ui";
import { elapsedMs, pause, type TimerState } from "@/features/timers/engine";
import { nowMs } from "@/lib/time";
import { CountdownRunner, StopwatchRunner } from "@/features/timers/runners";

function useWeek() {
  const { profile, program } = useActive();
  const today = useToday();
  return Math.min(program.weeks, Math.max(1, programWeek(profile.startDate, today) ?? 1));
}

// ---------- Steps ----------

export function StepsTab() {
  const { profile, program } = useActive();
  const today = useToday();
  const week = useWeek();
  const [open, setOpen] = useState(false);
  const daily = useLiveQuery(
    () => db.dailyNumbers.where("profileId").equals(profile.id).toArray(),
    [profile.id],
  );
  if (!daily) return null;
  const byDate = new Map(daily.map((d) => [d.date, d.steps]));
  const last7 = Array.from({ length: 7 }, (_, i) => addDays(today, i - 6));
  const avg = weeklyDailyAverage(daily, profile.startDate, week, "steps");
  const target = stepTarget(program, week);
  const weeks = Array.from({ length: week }, (_, i) => i + 1);

  return (
    <div className="space-y-4">
      <Card>
        <p className="text-sm font-semibold text-muted">This week's daily average</p>
        <p className="text-3xl font-bold text-ink tabular-nums">
          {avg === null ? "–" : Math.round(avg).toLocaleString()}
        </p>
        {target !== null && (
          <p
            className={`font-semibold ${avg !== null && avg >= target ? "text-[#067647]" : "text-muted"}`}
          >
            Target {target.toLocaleString()} a day{avg !== null && avg >= target ? " ✓" : ""}
          </p>
        )}
        <p className="mt-2 text-sm text-muted">
          <RichText text={program.tracking.steps.text} />
        </p>
        <Button className="mt-3 w-full" onClick={() => setOpen(true)}>
          Log steps
        </Button>
      </Card>
      <Card>
        <p className="mb-2 font-bold text-ink">Last 7 days</p>
        <BarChart
          title="Steps over the last 7 days"
          bars={last7.map((d) => ({
            label: weekdayOf(d).slice(0, 2).toUpperCase(),
            value: byDate.get(d) ?? null,
          }))}
          target={
            target !== null ? { value: target, label: `${(target / 1000).toFixed(0)}k` } : undefined
          }
          yFormat={(v) => (v >= 1000 ? `${Math.round(v / 1000)}k` : String(v))}
        />
      </Card>
      <Card className="p-0">
        <table className="w-full text-sm">
          <caption className="sr-only">Weekly step averages compared with the targets</caption>
          <thead>
            <tr className="bg-surface-2 text-left text-xs font-bold text-muted uppercase">
              <th className="px-3 py-2">Week</th>
              <th className="px-3 py-2 text-right">Average</th>
              <th className="px-3 py-2 text-right">Target</th>
            </tr>
          </thead>
          <tbody>
            {weeks.map((w) => {
              const a = weeklyDailyAverage(daily, profile.startDate, w, "steps");
              const t = stepTarget(program, w);
              return (
                <tr key={w} className="border-t border-line">
                  <td className="px-3 py-2 font-semibold text-ink">{w}</td>
                  <td className="px-3 py-2 text-right font-semibold text-ink tabular-nums">
                    {a === null ? "–" : Math.round(a).toLocaleString()}
                    {a !== null && t !== null && a >= t ? " ✓" : ""}
                  </td>
                  <td className="px-3 py-2 text-right text-muted tabular-nums">
                    {t?.toLocaleString() ?? "–"}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
      {open && <LogStepsSheet onClose={() => setOpen(false)} current={(d) => byDate.get(d)} />}
    </div>
  );
}

function LogStepsSheet({
  onClose,
  current,
}: {
  onClose: () => void;
  current: (date: string) => number | undefined;
}) {
  const { profile } = useActive();
  const today = useToday();
  const [date, setDate] = useState(today);
  const [steps, setSteps] = useState<number | undefined>(current(today));
  return (
    <Sheet open title="Log steps" onClose={onClose}>
      <div className="space-y-4">
        <Field label="Date">
          <input
            type="date"
            className={inputClass}
            value={date}
            max={today}
            onChange={(e) => {
              setDate(e.target.value);
              setSteps(current(e.target.value));
            }}
          />
        </Field>
        <Stepper label="Steps" step={500} max={100_000} value={steps} onChange={setSteps} />
        <Button
          size="lg"
          className="w-full"
          disabled={steps === undefined}
          onClick={async () => {
            await logDaily(profile.id, date, { steps });
            onClose();
          }}
        >
          Save
        </Button>
      </div>
    </Sheet>
  );
}

// ---------- Strength check-ins (gym) ----------

export function StrengthTab() {
  const { profile, program } = useActive();
  const sc = program.tracking.strengthCheckins!;
  const data = useLiveQuery(async () => {
    const [logs, sets] = await Promise.all([
      db.sessionLogs.where("profileId").equals(profile.id).toArray(),
      db.setLogs.where("profileId").equals(profile.id).toArray(),
    ]);
    return strengthCheckins(program, logs, sets);
  }, [profile.id, program]);
  return (
    <div className="space-y-3">
      <p className="text-muted">
        <RichText text={sc.text} />
      </p>
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <caption className="sr-only">Best set per lift in each check-in week</caption>
          <thead>
            <tr className="bg-surface-2 text-left text-xs font-bold text-muted uppercase">
              <th className="px-3 py-2">Lift</th>
              {sc.weeks.map((w) => (
                <th key={w} className="px-2 py-2 text-right">
                  Wk {w}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data?.map((row) => (
              <tr key={row.exerciseId} className="border-t border-line">
                <td className="px-3 py-2 font-semibold text-ink">
                  {program.exercises[row.exerciseId]?.name}
                </td>
                {row.byWeek.map((s, i) => (
                  <td key={i} className="px-2 py-2 text-right text-ink tabular-nums">
                    {s ? (
                      `${s.weightKg ?? 0}×${s.reps ?? 0}`
                    ) : (
                      <span className="text-muted">–</span>
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

// ---------- Fitness tests (home) ----------

export function FitnessTab() {
  const { profile, program } = useActive();
  const week = useWeek();
  const ft = program.tracking.fitnessTests!;
  const results = useLiveQuery(
    () => db.fitnessTests.where("profileId").equals(profile.id).toArray(),
    [profile.id],
  );
  const [testing, setTesting] = useState<FitnessTest | null>(null);
  // Results go to the latest test week reached (a test done a few days late still counts).
  const testWeek = [...ft.weeks].reverse().find((w) => w <= week) ?? ft.weeks[0]!;
  const nextWeek = ft.weeks.find((w) => w > week);
  const value = (w: number, id: string) =>
    results?.find((r) => r.week === w && r.testId === id)?.value;

  return (
    <div className="space-y-3">
      <p className="text-muted">
        <RichText text={ft.text} />
      </p>
      <p className="font-semibold text-ink">
        {testWeek === week
          ? `This is a test week (week ${week}).`
          : `New results count for week ${testWeek}.${nextWeek ? ` Next tests: week ${nextWeek}.` : ""}`}
      </p>
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <caption className="sr-only">Fitness test results by week</caption>
          <thead>
            <tr className="bg-surface-2 text-left text-xs font-bold text-muted uppercase">
              <th className="px-3 py-2">Test</th>
              {ft.weeks.map((w) => (
                <th key={w} className="px-2 py-2 text-right">
                  Wk {w}
                </th>
              ))}
              <th className="px-2 py-2" />
            </tr>
          </thead>
          <tbody>
            {ft.tests.map((t) => (
              <tr key={t.id} className="border-t border-line">
                <td className="px-3 py-2">
                  <span className="block font-semibold text-ink">{t.label}</span>
                  <span className="text-xs text-muted">{t.unit}</span>
                </td>
                {ft.weeks.map((w) => (
                  <td key={w} className="px-2 py-2 text-right text-ink tabular-nums">
                    {value(w, t.id) ?? <span className="text-muted">–</span>}
                  </td>
                ))}
                <td className="px-2 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => setTesting(t)}
                    className="min-h-10 rounded-lg px-2 font-semibold text-accent-ink"
                    aria-label={`Do the ${t.label} test`}
                  >
                    Test
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      {testing && (
        <FitnessTestSheet
          test={testing}
          week={testWeek}
          onClose={() => setTesting(null)}
          onSave={async (v) => {
            const existing = results?.find((r) => r.week === testWeek && r.testId === testing.id);
            if (existing) await db.fitnessTests.update(existing.id, { value: v });
            else
              await db.fitnessTests.add({
                id: newId(),
                profileId: profile.id,
                week: testWeek,
                testId: testing.id,
                value: v,
              });
            setTesting(null);
          }}
        />
      )}
    </div>
  );
}

function FitnessTestSheet({
  test,
  week,
  onClose,
  onSave,
}: {
  test: FitnessTest;
  week: number;
  onClose: () => void;
  onSave: (v: number) => void;
}) {
  const { program } = useActive();
  const [timer, setTimer] = useState<TimerState | null>(null);
  const [value, setValue] = useState<number | undefined>();
  const [timedDone, setTimedDone] = useState(false);
  const exercise = test.exerciseId ? program.exercises[test.exerciseId] : undefined;
  return (
    <Sheet open title={`${test.label} · week ${week}`} onClose={onClose}>
      <div className="space-y-4">
        {exercise && <p className="text-sm text-muted">{exercise.cues[0]}</p>}
        {test.method === "stopwatch" && (
          <StopwatchRunner
            timer={timer}
            setTimer={setTimer}
            targetMin={[0, 2]}
            label="Hold"
            onFinish={() => {
              if (timer) {
                setValue(Math.round(elapsedMs(timer, nowMs()) / 1000));
                setTimer(pause(timer, nowMs()));
              }
            }}
          />
        )}
        {test.method === "timed-count" && !timedDone && (
          <CountdownRunner
            timer={timer}
            setTimer={setTimer}
            seconds={test.durationSec ?? 60}
            label="Go!"
            onDone={() => setTimedDone(true)}
          />
        )}
        <Stepper
          label={`Result (${test.unit})`}
          unit={test.unit === "km" ? "km" : test.unit === "seconds" ? "s" : undefined}
          step={test.unit === "km" ? 0.1 : 1}
          decimals={test.unit === "km" ? 2 : 0}
          max={10_000}
          value={value}
          onChange={setValue}
        />
        <Button
          size="lg"
          className="w-full"
          disabled={value === undefined}
          onClick={() => value !== undefined && onSave(value)}
        >
          Save result
        </Button>
      </div>
    </Sheet>
  );
}

// ---------- Wins ----------

export function WinsTab() {
  const { profile, program } = useActive();
  const today = useToday();
  const wins = useLiveQuery(
    () => db.wins.where("profileId").equals(profile.id).toArray(),
    [profile.id],
  );
  const [editing, setEditing] = useState<{ label: string; custom: boolean } | null>(null);
  const [custom, setCustom] = useState("");
  if (!wins) return null;
  const labels = [
    ...program.tracking.wins.map((label) => ({ label, custom: false })),
    ...wins.filter((w) => w.custom).map((w) => ({ label: w.label, custom: true })),
  ];
  const record = (label: string) => wins.find((w) => w.label === label);
  const achieved = labels.filter((l) => record(l.label)?.achievedOn).length;

  return (
    <div className="space-y-4">
      <p className="text-muted">
        Wins that aren't on the scale. {achieved} of {labels.length} so far.
      </p>
      <ul className="space-y-2">
        {labels.map((l) => {
          const r = record(l.label);
          const done = !!r?.achievedOn;
          return (
            <li key={l.label}>
              <button
                type="button"
                onClick={() => setEditing(l)}
                className={`flex min-h-14 w-full items-center gap-3 rounded-2xl p-3 text-left ring-1 ${
                  done ? "bg-accent-soft ring-transparent" : "bg-surface ring-line"
                }`}
              >
                <span
                  className={`flex size-8 shrink-0 items-center justify-center rounded-full text-sm ${
                    done ? "bg-accent-strong text-on-accent" : "ring-2 ring-line"
                  }`}
                  aria-hidden="true"
                >
                  {done ? "✓" : ""}
                </span>
                <span className="flex-1">
                  <span className="block font-semibold text-ink">
                    <RichText text={l.label} />
                  </span>
                  {done && (
                    <span className="block text-sm text-muted">
                      {r!.achievedOn}
                      {r!.note ? ` · ${r!.note}` : ""}
                    </span>
                  )}
                </span>
                <span className="sr-only">{done ? "achieved" : "not yet"}</span>
              </button>
            </li>
          );
        })}
      </ul>
      <div className="flex gap-2">
        <input
          aria-label="Add your own win"
          placeholder="Add your own win"
          className={inputClass}
          value={custom}
          onChange={(e) => setCustom(e.target.value)}
        />
        <Button
          disabled={!custom.trim()}
          onClick={async () => {
            await db.wins.add({
              id: newId(),
              profileId: profile.id,
              label: custom.trim(),
              custom: true,
            });
            setCustom("");
          }}
        >
          Add
        </Button>
      </div>
      {editing && (
        <WinSheet
          label={editing.label}
          current={record(editing.label)}
          today={today}
          onClose={() => setEditing(null)}
          onSave={async (achievedOn, note) => {
            const r = record(editing.label);
            if (r) await db.wins.update(r.id, { achievedOn, note });
            else
              await db.wins.add({
                id: newId(),
                profileId: profile.id,
                label: editing.label,
                custom: editing.custom,
                ...(achievedOn ? { achievedOn } : {}),
                ...(note ? { note } : {}),
              });
            setEditing(null);
          }}
          onDelete={
            editing.custom
              ? async () => {
                  const r = record(editing.label);
                  if (r) await db.wins.delete(r.id);
                  setEditing(null);
                }
              : undefined
          }
        />
      )}
    </div>
  );
}

function WinSheet({
  label,
  current,
  today,
  onClose,
  onSave,
  onDelete,
}: {
  label: string;
  current?: { achievedOn?: string; note?: string };
  today: string;
  onClose: () => void;
  onSave: (achievedOn: string | undefined, note: string | undefined) => void;
  onDelete?: () => void;
}) {
  const [date, setDate] = useState(current?.achievedOn ?? today);
  const [note, setNote] = useState(current?.note ?? "");
  return (
    <Sheet open title="A win!" onClose={onClose}>
      <div className="space-y-4">
        <p className="font-semibold text-ink">
          <RichText text={label} />
        </p>
        <Field label="Date">
          <input
            type="date"
            className={inputClass}
            value={date}
            max={today}
            onChange={(e) => setDate(e.target.value)}
          />
        </Field>
        <Field label="Note (optional)">
          <input className={inputClass} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        <Button size="lg" className="w-full" onClick={() => onSave(date, note || undefined)}>
          {current?.achievedOn ? "Save" : "Mark as achieved"}
        </Button>
        {current?.achievedOn && (
          <Button
            variant="secondary"
            className="w-full"
            onClick={() => onSave(undefined, undefined)}
          >
            Not yet
          </Button>
        )}
        {onDelete && (
          <Button variant="ghost" className="w-full" onClick={onDelete}>
            Remove this win
          </Button>
        )}
      </div>
    </Sheet>
  );
}
