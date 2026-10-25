import { useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import type { Exercise } from "@/content/types";
import { db, newId } from "@/db/db";
import { updateProfile } from "@/db/repo";
import { setFlag, useFlag, useSetting } from "@/db/settings";
import type { SetLog } from "@/domain/model";
import {
  type LastPerformance,
  prefillSet,
  shouldShowDumbbellTip,
  suggestProgression,
  suggestionText,
} from "@/domain/progression";
import { nowMs } from "@/lib/time";
import { ExerciseMedia } from "@/components/ExerciseMedia";
import { RichText } from "@/components/RichText";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button, Callout } from "@/components/ui";
import { CountdownRunner } from "@/features/timers/runners";
import { elapsedMs, isRunning, newTimer, pause, start } from "@/features/timers/engine";
import { useNow } from "@/features/timers/useTimer";
import { itemAt, targetText, usePlayerCtx } from "./context";
import { plannedFor, setsText, useLastPerformance, weightStep } from "./history";
import { usePlayer } from "./store";
import { buildSteps, indexOfKey, type SetStep as SetStepT } from "./steps";

export function SetStep({ step }: { step: SetStepT }) {
  const ctx = usePlayerCtx();
  const { item, exercise } = itemAt(ctx, step.pairIndex, step.itemIndex);
  const last = useLastPerformance(ctx.profile.id, exercise.id, ctx.snap.sessionLogId);
  const sessionSets = useLiveQuery(
    () => db.setLogs.where("sessionLogId").equals(ctx.snap.sessionLogId).toArray(),
    [ctx.snap.sessionLogId],
  );
  if (last === undefined || sessionSets === undefined) return null;
  return (
    <SetForm
      key={`${step.key}:${exercise.id}`}
      step={step}
      exercise={exercise}
      itemCode={item.code}
      last={last}
      sessionSets={sessionSets}
    />
  );
}

function SetForm({
  step,
  exercise,
  itemCode,
  last,
  sessionSets,
}: {
  step: SetStepT;
  exercise: Exercise;
  itemCode: string;
  last: LastPerformance | null;
  sessionSets: SetLog[];
}) {
  const ctx = usePlayerCtx();
  const { program, profile, snap, resolved, steps } = ctx;
  const { item } = itemAt(ctx, step.pairIndex, step.itemIndex);
  const target = item.target;
  const barbellStep = useSetting("barbellStepKg") ?? 2.5;
  const dumbbellStep = useSetting("dumbbellStepKg") ?? 1;
  const tipShown = useFlag(profile.id, "dumbbellTip");

  const loggedId = snap.logged[step.key];
  const logged = sessionSets.find((s) => s.id === loggedId);
  const earlier = sessionSets
    .filter(
      (s) => s.exerciseId === exercise.id && s.blockType === "pair" && s.setIndex < step.setIndex,
    )
    .sort((a, b) => b.setIndex - a.setIndex)[0];
  const fromLast = prefillSet(last, step.setIndex);
  const equipmentKg =
    program.setting === "home"
      ? exercise.load === "dumbbell"
        ? profile.equipment.dumbbellKg
        : exercise.load === "kettlebell"
          ? profile.equipment.kettlebellKg
          : undefined
      : undefined;

  const init = logged ?? (last ? fromLast : undefined) ?? earlier;
  const [weightKg, setWeight] = useState<number | undefined>(
    init?.weightKg ?? earlier?.weightKg ?? equipmentKg,
  );
  const [reps, setReps] = useState<number | undefined>(init?.reps ?? earlier?.reps ?? target.max);
  const [seconds, setSeconds] = useState<number | undefined>(init?.seconds ?? target.max);
  const [sheet, setSheet] = useState<null | "swap" | "skip" | "dumbbells">(null);

  const update = usePlayer((s) => s.update);
  const next = usePlayer((s) => s.next);
  const goTo = usePlayer((s) => s.goTo);

  const lastPlanned = last
    ? {
        sets: last.sets,
        ...plannedFor(program, last.log, exercise.id, { sets: item.sets, max: target.max }),
      }
    : null;
  const suggestion =
    step.setIndex === 0 && !logged
      ? suggestProgression(exercise, program.setting, lastPlanned)
      : null;
  const showTip =
    step.setIndex === 0 && shouldShowDumbbellTip(program.id, exercise.id, lastPlanned, tipShown);
  const variation = snap.variations[exercise.id];

  async function done() {
    const id = loggedId ?? newId();
    const row: SetLog = {
      id,
      sessionLogId: snap.sessionLogId,
      profileId: profile.id,
      exerciseId: exercise.id,
      blockType: "pair",
      pairCode: item.code,
      setIndex: step.setIndex,
      perSide: target.perSide,
      isWarmup: false,
      completedAt: nowMs(),
      ...(exercise.logAs === "weight-reps" && weightKg !== undefined ? { weightKg } : {}),
      ...(exercise.logAs === "weight-reps" || exercise.logAs === "reps" ? { reps: reps ?? 0 } : {}),
      ...(exercise.logAs === "time" ? { seconds: seconds ?? 0 } : {}),
      ...(variation ? { variation } : {}),
    };
    await db.setLogs.put(row);
    update((s) => ({ ...s, logged: { ...s.logged, [step.key]: id } }));
    next(steps);
  }

  function changeMods(
    code: string,
    change: { sets?: number; exerciseId?: string; skipped?: string },
    note?: string,
  ) {
    const mods = { ...snap.mods, [code]: { ...snap.mods[code], ...change } };
    const newSteps = buildSteps(resolved, mods);
    update((s) => ({
      ...s,
      mods,
      notes: note ? [s.notes, note].filter(Boolean).join("\n") : s.notes,
    }));
    const s = usePlayer.getState().snap!;
    goTo(newSteps, indexOfKey(newSteps, s.stepKey, s.stepIndex));
  }

  const lastText = last && last.sets.length ? setsText(last.sets, exercise) : null;
  const dumbbellStepKg = weightStep(exercise, barbellStep, dumbbellStep);

  return (
    <div className="space-y-4">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-2xl leading-tight font-bold text-ink">
          <span className="mr-2 text-accent-ink">{item.code}</span>
          {exercise.name}
        </h2>
      </div>
      <ExerciseMedia exercise={exercise} className="h-48" />
      <div>
        <p className="text-lg font-bold text-ink">
          Set {step.setIndex + 1} of {step.setsTotal} · {targetText(target)}
        </p>
        {target.note && (
          <p className="text-ink">
            <RichText text={target.note} />
          </p>
        )}
        {lastText ? <p className="text-muted">Last time: {lastText}</p> : null}
        {variation && <p className="text-sm font-semibold text-accent-ink">Today: {variation}</p>}
      </div>

      {suggestion && (
        <Callout
          tone={
            suggestion.kind === "go-up" || suggestion.kind === "make-harder" ? "accent" : "info"
          }
        >
          <p>
            <RichText text={suggestionText(suggestion)} />
          </p>
          {suggestion.kind === "make-harder" && (
            <div className="mt-2 flex flex-wrap gap-2">
              {suggestion.options.map((o) => (
                <button
                  key={o}
                  type="button"
                  aria-pressed={variation === o}
                  onClick={() =>
                    update((s) => ({
                      ...s,
                      variations: { ...s.variations, [exercise.id]: variation === o ? "" : o },
                    }))
                  }
                  className={`min-h-11 rounded-full px-4 text-sm font-semibold ring-1 ${
                    variation === o
                      ? "bg-accent-strong text-on-accent ring-transparent"
                      : "bg-surface text-ink ring-line"
                  }`}
                >
                  {o}
                </button>
              ))}
            </div>
          )}
        </Callout>
      )}
      {showTip && (
        <Callout tone="accent" title="Time for heavier dumbbells?">
          <p>
            You've hit the top of the range at 5 kg. A pair of 8 kg dumbbells is a worthwhile buy.
          </p>
          <Button variant="secondary" className="mt-2" onClick={() => setSheet("dumbbells")}>
            Update my dumbbell weight
          </Button>
        </Callout>
      )}

      {exercise.logAs === "weight-reps" && (
        <div className="space-y-3">
          <Stepper
            label="Weight"
            unit="kg"
            step={dumbbellStepKg}
            decimals={2}
            max={500}
            value={weightKg}
            onChange={setWeight}
          />
          <Stepper
            label={target.perSide ? "Reps (each side)" : "Reps"}
            value={reps}
            onChange={setReps}
            max={200}
          />
        </div>
      )}
      {exercise.logAs === "reps" && (
        <Stepper
          label={target.perSide ? "Reps (each side)" : "Reps"}
          value={reps}
          onChange={setReps}
          max={500}
        />
      )}
      {exercise.logAs === "time" && (
        <HoldTimer
          target={target.max}
          seconds={seconds}
          setSeconds={setSeconds}
          perSide={target.perSide}
        />
      )}

      <Button size="lg" className="w-full text-xl" onClick={() => void done()}>
        {logged ? "Save" : "Done"}
      </Button>

      <div className="grid grid-cols-4 gap-1 text-sm">
        <SmallAction label="Swap" onClick={() => setSheet("swap")} />
        <SmallAction label="Skip" onClick={() => setSheet("skip")} />
        <SmallAction
          label="+ Set"
          onClick={() => changeMods(itemCode, { sets: step.setsTotal + 1 })}
        />
        <SmallAction
          label="− Set"
          disabled={step.setsTotal <= step.setIndex + 1}
          onClick={() => changeMods(itemCode, { sets: step.setsTotal - 1 })}
        />
      </div>

      <SwapSheet
        open={sheet === "swap"}
        current={exercise}
        onClose={() => setSheet(null)}
        onSwap={(to, reason) => {
          setSheet(null);
          changeMods(
            itemCode,
            { exerciseId: to.id },
            `Swapped ${itemCode} ${exercise.name} → ${to.name} (${reason})`,
          );
        }}
      />
      <ReasonSheet
        open={sheet === "skip"}
        title={`Skip ${exercise.name}?`}
        onClose={() => setSheet(null)}
        onPick={(reason) => {
          setSheet(null);
          changeMods(
            itemCode,
            { skipped: reason },
            `Skipped ${itemCode} ${exercise.name} (${reason})`,
          );
        }}
      />
      {sheet === "dumbbells" && (
        <DumbbellSheet
          initial={profile.equipment.dumbbellKg ?? 5}
          onClose={() => setSheet(null)}
          onSave={async (kg) => {
            await updateProfile(profile.id, {
              equipment: { ...profile.equipment, dumbbellKg: kg },
            });
            await setFlag(profile.id, "dumbbellTip");
            setWeight(kg);
            setSheet(null);
          }}
        />
      )}
    </div>
  );
}

function SmallAction({
  label,
  onClick,
  disabled,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="min-h-12 rounded-xl font-semibold text-accent-ink active:bg-surface-2 disabled:opacity-40"
    >
      {label}
    </button>
  );
}

/** Countdown for a timed hold, with a stopwatch fallback for holds past the target. */
function HoldTimer({
  target,
  seconds,
  setSeconds,
  perSide,
}: {
  target: number;
  seconds: number | undefined;
  setSeconds: (v: number | undefined) => void;
  perSide: boolean;
}) {
  const timer = usePlayer((s) => s.snap?.timer ?? null);
  const setTimer = usePlayer((s) => s.setTimer);
  const [mode, setMode] = useState<"countdown" | "stopwatch">("countdown");
  const running = !!timer && isRunning(timer);
  const now = useNow(running);

  return (
    <div className="space-y-3">
      {mode === "countdown" ? (
        <CountdownRunner
          timer={timer}
          setTimer={setTimer}
          seconds={target}
          label={perSide ? "Hold · each side" : "Hold"}
          onDone={() => setSeconds(target)}
        />
      ) : (
        <div className="space-y-3 text-center">
          <p className="text-7xl font-bold text-ink tabular-nums" role="timer">
            {Math.floor((timer ? elapsedMs(timer, now) : 0) / 1000)} s
          </p>
          <Button
            size="lg"
            className="w-full"
            onClick={() => {
              if (running) {
                const t = pause(timer!, Date.now());
                setTimer(t);
                setSeconds(Math.round(elapsedMs(t, Date.now()) / 1000));
              } else setTimer(start(timer ?? newTimer(null), Date.now()));
            }}
          >
            {running ? "Stop" : "Start stopwatch"}
          </Button>
        </div>
      )}
      <button
        type="button"
        className="block w-full text-center text-sm font-semibold text-accent-ink"
        onClick={() => {
          setTimer(null);
          setMode(mode === "countdown" ? "stopwatch" : "countdown");
        }}
      >
        {mode === "countdown" ? "Use a stopwatch instead" : "Use the countdown"}
      </button>
      <Stepper
        label="Seconds held"
        unit="s"
        step={5}
        max={600}
        value={seconds}
        onChange={setSeconds}
      />
    </div>
  );
}

const SWAP_REASONS = ["Machine or bench busy", "Pain or discomfort", "No equipment", "Other"];

function SwapSheet({
  open,
  current,
  onClose,
  onSwap,
}: {
  open: boolean;
  current: Exercise;
  onClose: () => void;
  onSwap: (to: Exercise, reason: string) => void;
}) {
  const { program } = usePlayerCtx();
  const [reason, setReason] = useState(SWAP_REASONS[0]!);
  const section = program.guideSections.find((g) => g.exerciseIds.includes(current.id));
  const similar = (section?.exerciseIds ?? []).filter((id) => id !== current.id);
  const others = Object.values(program.exercises)
    .filter((e) => e.id !== current.id && !similar.includes(e.id) && e.logAs === current.logAs)
    .map((e) => e.id);
  const options = [...similar, ...others]
    .map((id) => program.exercises[id]!)
    .filter((e) => e.logAs !== "none");
  return (
    <Sheet open={open} title={`Swap ${current.name}`} onClose={onClose}>
      <p className="mb-2 text-sm font-semibold text-muted">Why?</p>
      <ReasonChips value={reason} onChange={setReason} />
      <p className="mt-4 mb-2 text-sm font-semibold text-muted">Do this instead</p>
      <ul className="divide-y divide-line rounded-xl bg-surface ring-1 ring-line">
        {options.map((e) => (
          <li key={e.id}>
            <button
              type="button"
              onClick={() => onSwap(e, reason)}
              className="flex min-h-14 w-full flex-col justify-center px-4 py-2 text-left"
            >
              <span className="font-semibold text-ink">{e.name}</span>
              <span className="text-sm text-muted">{e.muscles.join(" · ")}</span>
            </button>
          </li>
        ))}
      </ul>
    </Sheet>
  );
}

function ReasonChips({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <div className="flex flex-wrap gap-2">
      {SWAP_REASONS.map((r) => (
        <button
          key={r}
          type="button"
          aria-pressed={value === r}
          onClick={() => onChange(r)}
          className={`min-h-11 rounded-full px-4 text-sm font-semibold ring-1 ${
            value === r
              ? "bg-accent-strong text-on-accent ring-transparent"
              : "bg-surface text-ink ring-line"
          }`}
        >
          {r}
        </button>
      ))}
    </div>
  );
}

function ReasonSheet({
  open,
  title,
  onClose,
  onPick,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  onPick: (reason: string) => void;
}) {
  const [reason, setReason] = useState(SWAP_REASONS[0]!);
  return (
    <Sheet open={open} title={title} onClose={onClose}>
      <p className="mb-2 text-sm font-semibold text-muted">Why?</p>
      <ReasonChips value={reason} onChange={setReason} />
      <Button size="lg" className="mt-5 w-full" onClick={() => onPick(reason)}>
        Skip the remaining sets
      </Button>
    </Sheet>
  );
}

function DumbbellSheet({
  initial,
  onClose,
  onSave,
}: {
  initial: number;
  onClose: () => void;
  onSave: (kg: number) => void;
}) {
  const [kg, setKg] = useState<number | undefined>(Math.max(initial, 8));
  return (
    <Sheet open title="Your dumbbells" onClose={onClose}>
      <Stepper
        label="Dumbbells (each)"
        unit="kg"
        step={0.5}
        decimals={1}
        max={50}
        value={kg}
        onChange={setKg}
      />
      <Button size="lg" className="mt-5 w-full" disabled={!kg} onClick={() => kg && onSave(kg)}>
        Save
      </Button>
    </Sheet>
  );
}
