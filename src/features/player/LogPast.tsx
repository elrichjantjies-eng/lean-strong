import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useActive, useToday } from "@/app/active";
import { db, newId } from "@/db/db";
import type { SessionLog, SetLog } from "@/domain/model";
import {
  completedSessionIds,
  dayPlanFor,
  phaseForWeek,
  programWeek,
  sessionOrder,
} from "@/domain/schedule";
import { resolveSession } from "@/domain/session";
import { Screen } from "@/components/Screen";
import { Button, Card, Field, inputClass } from "@/components/ui";
import { targetText } from "./context";
import { nowMs } from "@/lib/time";

type Row = { weightKg?: number; reps?: number; seconds?: number };

/** Log a workout done without the phone: pick a date and session, fill sets in a compact form. */
export function LogPastScreen() {
  const { profile, program } = useActive();
  const today = useToday();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [date, setDate] = useState(params.get("date") ?? today);
  const planned = dayPlanFor(program, date).sessionId;
  const [sessionId, setSessionId] = useState(
    params.get("session") ?? planned ?? sessionOrder(program)[0]!,
  );
  const week = Math.min(program.weeks, Math.max(1, programWeek(profile.startDate, date) ?? 1));
  const resolved = resolveSession(program, sessionId, week);
  const [rows, setRows] = useState<Record<string, Row[]>>({});
  const [saving, setSaving] = useState(false);

  const setCell = (code: string, i: number, field: keyof Row, value: string) =>
    setRows((r) => {
      const list = [...(r[code] ?? [])];
      const n = parseFloat(value.replace(",", "."));
      list[i] = { ...list[i], [field]: Number.isFinite(n) ? n : undefined };
      return { ...r, [code]: list };
    });

  async function save() {
    setSaving(true);
    const weekLogs = await db.sessionLogs.where({ profileId: profile.id, week }).toArray();
    const log: SessionLog = {
      id: newId(),
      profileId: profile.id,
      programId: program.id,
      sessionId,
      week,
      phase: phaseForWeek(program, week).number,
      date,
      startedAt: nowMs(),
      finishedAt: nowMs(),
      status: "completed",
      isExtra: completedSessionIds(weekLogs, week).has(sessionId),
      notes: "Logged afterwards",
    };
    const sets: SetLog[] = [];
    for (const pair of resolved.pairs) {
      for (const item of pair.items) {
        const ex = program.exercises[item.exerciseId]!;
        (rows[item.code] ?? []).forEach((r, setIndex) => {
          if (!r || (r.reps === undefined && r.seconds === undefined)) return;
          sets.push({
            id: newId(),
            sessionLogId: log.id,
            profileId: profile.id,
            exerciseId: ex.id,
            blockType: "pair",
            pairCode: item.code,
            setIndex,
            perSide: item.target.perSide,
            isWarmup: false,
            completedAt: nowMs(),
            ...(r.weightKg !== undefined ? { weightKg: r.weightKg } : {}),
            ...(r.reps !== undefined ? { reps: r.reps } : {}),
            ...(r.seconds !== undefined ? { seconds: r.seconds } : {}),
          });
        });
      }
    }
    await db.transaction("rw", db.sessionLogs, db.setLogs, async () => {
      await db.sessionLogs.add(log);
      await db.setLogs.bulkAdd(sets);
    });
    navigate("/", { replace: true });
  }

  const cell =
    "h-11 w-full rounded-lg bg-surface px-2 text-center text-ink tabular-nums ring-1 ring-line";
  return (
    <Screen title="Log a past workout">
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <Field label="Date">
            <input
              type="date"
              className={inputClass}
              value={date}
              max={today}
              onChange={(e) => setDate(e.target.value)}
            />
          </Field>
          <Field label="Session">
            <select
              className={inputClass}
              value={sessionId}
              onChange={(e) => setSessionId(e.target.value)}
            >
              {sessionOrder(program).map((id) => (
                <option key={id} value={id}>
                  {program.sessions[id]!.title}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <p className="text-sm text-muted">
          Week {week} · Phase {resolved.p + 1}. Leave a row empty if you didn't do that set.
        </p>
        {resolved.pairs.flatMap((pair) =>
          pair.items.map((item) => {
            const ex = program.exercises[item.exerciseId]!;
            if (ex.logAs === "none") return null;
            return (
              <Card key={item.code}>
                <p className="font-bold text-ink">
                  <span className="text-accent-ink">{item.code}</span> {ex.name}
                </p>
                <p className="mb-2 text-sm text-muted">
                  {item.sets} × {targetText(item.target)}
                </p>
                <div className="space-y-2">
                  {Array.from({ length: item.sets }, (_, i) => (
                    <div key={i} className="flex items-center gap-2">
                      <span className="w-12 text-sm text-muted">Set {i + 1}</span>
                      {ex.logAs === "weight-reps" && (
                        <input
                          aria-label={`${item.code} set ${i + 1} weight`}
                          placeholder="kg"
                          inputMode="decimal"
                          className={cell}
                          onChange={(e) => setCell(item.code, i, "weightKg", e.target.value)}
                        />
                      )}
                      {ex.logAs === "time" ? (
                        <input
                          aria-label={`${item.code} set ${i + 1} seconds`}
                          placeholder="sec"
                          inputMode="numeric"
                          className={cell}
                          onChange={(e) => setCell(item.code, i, "seconds", e.target.value)}
                        />
                      ) : (
                        <input
                          aria-label={`${item.code} set ${i + 1} reps`}
                          placeholder="reps"
                          inputMode="numeric"
                          className={cell}
                          onChange={(e) => setCell(item.code, i, "reps", e.target.value)}
                        />
                      )}
                    </div>
                  ))}
                </div>
              </Card>
            );
          }),
        )}
        <Button size="lg" className="w-full" disabled={saving} onClick={() => void save()}>
          Save workout
        </Button>
      </div>
    </Screen>
  );
}
