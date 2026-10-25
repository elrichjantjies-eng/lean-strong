import { useState } from "react";
import { db, newId } from "@/db/db";
import { logWalk } from "@/db/repo";
import type { SetLog } from "@/domain/model";
import type { ResolvedCircuit } from "@/domain/session";
import { ExerciseDemo, ExerciseMedia } from "@/components/ExerciseMedia";
import { Icon } from "@/components/Icon";
import { RichText } from "@/components/RichText";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button, Callout, Card } from "@/components/ui";
import { CountdownRunner, IntervalRunner, StopwatchRunner } from "@/features/timers/runners";
import { itemAt, targetText, usePlayerCtx } from "./context";
import { usePlayer } from "./store";
import { SWITCH_SECONDS, type Step } from "./steps";

function useStepActions() {
  const ctx = usePlayerCtx();
  const timer = usePlayer((s) => s.snap?.timer ?? null);
  const setTimer = usePlayer((s) => s.setTimer);
  const next = usePlayer((s) => s.next);
  return { ctx, timer, setTimer, next: () => next(ctx.steps) };
}

async function logActivity(
  ctx: ReturnType<typeof usePlayerCtx>,
  kind: "finisher" | "circuit" | "warmup",
  extra: { minutes?: number; rounds?: number },
) {
  await db.activityLogs.add({
    id: newId(),
    profileId: ctx.profile.id,
    date: ctx.snap.date,
    kind,
    sessionLogId: ctx.snap.sessionLogId,
    ...extra,
  });
}

// ---------- Warm-up ----------

export function RopeStep() {
  const { ctx, timer, setTimer, next } = useStepActions();
  const s = ctx.resolved.session;
  const rope = ctx.resolved.rope;
  return (
    <div className="space-y-4">
      <StepTitle kicker="Warm-up" title={rope.activity} />
      <p className="text-ink">
        <RichText text={s.warmup.text} />
      </p>
      <IntervalRunner
        timer={timer}
        setTimer={setTimer}
        spec={rope}
        onComplete={() => {
          void logActivity(ctx, "warmup", { rounds: rope.rounds });
          next();
        }}
        onSkip={next}
      />
      {s.warmup.alternative && (
        <Callout title="No rope?">
          <p>
            <RichText text={s.warmup.alternative} />
          </p>
        </Callout>
      )}
    </div>
  );
}

export function MobilityStep() {
  const { ctx, next } = useStepActions();
  const items = ctx.resolved.session.warmup.mobility;
  const [done, setDone] = useState<boolean[]>(items.map(() => false));
  return (
    <div className="space-y-4">
      <StepTitle kicker="Warm-up" title="Mobility" />
      <Card className="py-1">
        <ul>
          {items.map((m, i) => (
            <li key={m} className="border-b border-line last:border-0">
              <label className="flex min-h-14 items-center gap-3 py-2">
                <input
                  type="checkbox"
                  className="size-6 shrink-0 accent-[var(--app-accent)]"
                  checked={done[i]}
                  onChange={(e) =>
                    setDone((d) => d.map((v, j) => (j === i ? e.target.checked : v)))
                  }
                />
                <span className="text-ink">
                  <RichText text={m} />
                </span>
              </label>
            </li>
          ))}
        </ul>
      </Card>
      <Button size="lg" className="w-full" onClick={next}>
        Continue
      </Button>
    </div>
  );
}

export function WarmupSetsStep() {
  const { ctx, next } = useStepActions();
  return (
    <div className="space-y-4">
      <StepTitle kicker="Warm-up" title="Warm-up sets" />
      <Callout tone="accent">
        <p>
          <RichText text={ctx.resolved.session.warmup.warmupSets ?? ""} />
        </p>
      </Callout>
      <Button size="lg" className="w-full" onClick={next}>
        Start power sets
      </Button>
    </div>
  );
}

// ---------- Between sets ----------

/** "Next: A2 Lat Pulldown · Set 1 of 3" for the step after `index`. */
function NextUp({ index }: { index: number }) {
  const ctx = usePlayerCtx();
  const step = ctx.steps[index + 1];
  if (!step) return null;
  let text: string;
  let exerciseId: string | null = null;
  if (step.type === "set") {
    const { item, exercise } = itemAt(ctx, step.pairIndex, step.itemIndex);
    text = `${item.code} ${exercise.name} · Set ${step.setIndex + 1} of ${step.setsTotal}`;
    exerciseId = exercise.id;
  } else if (step.type === "finisher") text = `Finisher: ${ctx.resolved.finisher?.name}`;
  else if (step.type === "waist") text = ctx.resolved.waistCircuit?.title ?? "Circuit";
  else if (step.type === "cooldown") text = "Cool-down";
  else text = "Keep going";
  const exercise = exerciseId ? ctx.program.exercises[exerciseId] : null;
  return (
    <Card className="flex items-center gap-3">
      {exercise && <ExerciseDemo exercise={exercise} className="size-16 shrink-0" />}
      <div>
        <p className="text-xs font-bold tracking-wide text-muted uppercase">Next</p>
        <p className="font-semibold text-ink">{text}</p>
      </div>
    </Card>
  );
}

export function SwitchStep({ step }: { step: Extract<Step, { type: "switch" }> }) {
  const { ctx, timer, setTimer, next } = useStepActions();
  return (
    <div className="space-y-4">
      <StepTitle kicker={`Pair ${ctx.resolved.pairs[step.pairIndex]!.code}`} title="Switch" />
      <CountdownRunner
        timer={timer}
        setTimer={setTimer}
        seconds={SWITCH_SECONDS}
        autoStart
        label="Switch"
        onDone={next}
        onSkip={next}
        skipLabel="I'm ready"
      />
      <NextUp index={ctx.index} />
    </div>
  );
}

export function RestStep({ step }: { step: Extract<Step, { type: "rest" }> }) {
  const { ctx, timer, setTimer, next } = useStepActions();
  return (
    <div className="space-y-4">
      <StepTitle
        kicker={`Pair ${ctx.resolved.pairs[step.pairIndex]!.code} · round ${step.setIndex + 1} done`}
        title="Rest"
      />
      <CountdownRunner
        timer={timer}
        setTimer={setTimer}
        seconds={step.seconds}
        autoStart
        adjustable
        label="Rest"
        onDone={next}
        onSkip={next}
        skipLabel="Skip rest"
      />
      <NextUp index={ctx.index} />
    </div>
  );
}

// ---------- Circuits (finisher circuit, waist circuit) ----------

export function CircuitRunner({
  circuit,
  prefix,
  blockType,
  onFinished,
}: {
  circuit: ResolvedCircuit;
  prefix: string;
  blockType: "finisher" | "circuit";
  onFinished: () => void;
}) {
  const { ctx, timer, setTimer } = useStepActions();
  const update = usePlayer((s) => s.update);
  const round = ctx.snap.sub[`${prefix}.round`] ?? 0;
  const stepI = ctx.snap.sub[`${prefix}.step`] ?? 0;
  const resting = ctx.snap.sub[`${prefix}.rest`] === 1;
  const move = circuit.steps[stepI]!;
  const exercise = ctx.program.exercises[move.exerciseId]!;

  const setSub = (values: Record<string, number>) =>
    update((s) => ({
      ...s,
      timer: null,
      sub: {
        ...s.sub,
        ...Object.fromEntries(Object.entries(values).map(([k, v]) => [`${prefix}.${k}`, v])),
      },
    }));

  async function completeMove(seconds?: number) {
    const row: SetLog = {
      id: newId(),
      sessionLogId: ctx.snap.sessionLogId,
      profileId: ctx.profile.id,
      exerciseId: move.exerciseId,
      blockType,
      pairCode: prefix,
      setIndex: round * circuit.steps.length + stepI,
      perSide: move.target.perSide,
      isWarmup: false,
      completedAt: Date.now(),
      ...(move.target.kind === "time"
        ? { seconds: seconds ?? move.target.max }
        : { reps: move.target.max }),
    };
    await db.setLogs.add(row);
    const lastMove = stepI === circuit.steps.length - 1;
    if (!lastMove) return setSub({ step: stepI + 1 });
    if (round === circuit.rounds - 1) {
      await logActivity(ctx, blockType, { rounds: circuit.rounds });
      return onFinished();
    }
    setSub({ rest: 1 });
  }

  const header = (
    <p className="text-lg font-bold text-ink">
      Round {round + 1} of {circuit.rounds}
      {!resting && ` · Move ${stepI + 1} of ${circuit.steps.length}`}
    </p>
  );

  if (resting) {
    return (
      <div className="space-y-4">
        {header}
        <CountdownRunner
          timer={timer}
          setTimer={setTimer}
          seconds={circuit.restSec}
          autoStart
          adjustable
          label="Rest"
          onDone={() => setSub({ rest: 0, step: 0, round: round + 1 })}
          onSkip={() => setSub({ rest: 0, step: 0, round: round + 1 })}
          skipLabel="Start next round"
        />
      </div>
    );
  }

  return (
    <div className="space-y-4" key={`${round}:${stepI}`}>
      {header}
      <h3 className="text-2xl font-bold text-ink">{exercise.name}</h3>
      <ExerciseMedia exercise={exercise} className="h-44" />
      <p className="text-lg font-semibold text-ink">{targetText(move.target)}</p>
      {move.target.kind === "time" ? (
        <CountdownRunner
          timer={timer}
          setTimer={setTimer}
          seconds={move.target.max}
          label={move.target.perSide ? "Hold · each side" : "Hold"}
          onDone={() => void completeMove(move.target.max)}
          onSkip={() => void completeMove()}
          skipLabel="Done"
        />
      ) : (
        <Button size="lg" className="w-full text-xl" onClick={() => void completeMove()}>
          Done
        </Button>
      )}
    </div>
  );
}

export function WaistStep() {
  const { ctx, next } = useStepActions();
  const circuit = ctx.resolved.waistCircuit!;
  return (
    <div className="space-y-4">
      <StepTitle kicker="Circuit" title={circuit.title} />
      {circuit.description && (
        <p className="text-muted">
          <RichText text={circuit.description} />
        </p>
      )}
      <CircuitRunner circuit={circuit} prefix="waist" blockType="circuit" onFinished={next} />
    </div>
  );
}

// ---------- Finisher ----------

export function FinisherStep() {
  const { ctx, timer, setTimer, next } = useStepActions();
  const setSub = usePlayer((s) => s.setSub);
  const f = ctx.resolved.finisher!;
  const header = (
    <>
      <StepTitle kicker="Finisher" title={f.name} />
      <p className="text-ink">
        <RichText text={f.description} />
      </p>
    </>
  );

  if (f.kind === "intervals") {
    return (
      <div className="space-y-4">
        {header}
        <IntervalRunner
          timer={timer}
          setTimer={setTimer}
          spec={f.interval}
          onComplete={() => {
            void logActivity(ctx, "finisher", {
              rounds: f.interval.rounds,
              minutes: Math.round(
                (f.interval.rounds * (f.interval.workSec + f.interval.restSec)) / 60,
              ),
            });
            next();
          }}
          onSkip={next}
        />
        {f.alternative && (
          <Callout title="Alternative">
            <p>
              <RichText text={f.alternative} />
            </p>
          </Callout>
        )}
      </div>
    );
  }

  if (f.kind === "circuit") {
    return (
      <div className="space-y-4">
        {header}
        <CircuitRunner
          circuit={f.circuit}
          prefix="finisher"
          blockType="finisher"
          onFinished={next}
        />
      </div>
    );
  }

  const choice = ctx.snap.sub["finisher.choice"];
  const option = choice !== undefined ? f.options[choice] : undefined;
  if (!option) {
    return (
      <div className="space-y-4">
        {header}
        <p className="font-semibold text-ink">Pick one:</p>
        {f.options.map((o, i) => (
          <Button
            key={o.label}
            variant="secondary"
            size="lg"
            className="w-full"
            onClick={() => setSub("finisher.choice", i)}
          >
            {o.label}
          </Button>
        ))}
        <Button variant="ghost" className="w-full" onClick={next}>
          Skip the finisher
        </Button>
      </div>
    );
  }
  return (
    <div className="space-y-4">
      <StepTitle kicker="Finisher" title={option.label} />
      {"interval" in option ? (
        <IntervalRunner
          timer={timer}
          setTimer={setTimer}
          spec={option.interval}
          onComplete={() => {
            void logActivity(ctx, "finisher", { rounds: option.interval.rounds });
            next();
          }}
          onSkip={next}
        />
      ) : (
        <StopwatchRunner
          timer={timer}
          setTimer={setTimer}
          targetMin={option.durationMin}
          label="Cardio"
          onFinish={(minutes) => {
            void logActivity(ctx, "finisher", { minutes });
            next();
          }}
        />
      )}
      <button
        type="button"
        className="block w-full text-center text-sm font-semibold text-accent-ink"
        onClick={() => setSub("finisher.choice", -1)}
      >
        Pick something else
      </button>
    </div>
  );
}

// ---------- Walk after ----------

export function WalkStep() {
  const { ctx, timer, setTimer, next } = useStepActions();
  const walk = ctx.resolved.session.walkAfter!;
  const [finishing, setFinishing] = useState<number | null>(null);
  return (
    <div className="space-y-4">
      <StepTitle kicker="Walk" title="Walking pad" />
      <p className="text-ink">
        <RichText text={walk.text} />
      </p>
      <StopwatchRunner
        timer={timer}
        setTimer={setTimer}
        targetMin={ctx.resolved.walkAfterMin!}
        label="Walk"
        color={ctx.program.theme.dayColors.walk}
        onFinish={(m) => setFinishing(m)}
      />
      {finishing !== null && (
        <WalkLogSheet
          minutes={finishing || ctx.resolved.walkAfterMin![1]}
          onClose={() => setFinishing(null)}
          onSave={async (minutes, distanceKm) => {
            await logWalk(ctx.profile.id, ctx.snap.date, minutes, {
              sessionLogId: ctx.snap.sessionLogId,
              ...(distanceKm ? { distanceKm } : {}),
            });
            setFinishing(null);
            next();
          }}
        />
      )}
    </div>
  );
}

export function WalkLogSheet({
  minutes: initial,
  onClose,
  onSave,
}: {
  minutes: number;
  onClose: () => void;
  onSave: (minutes: number, distanceKm?: number) => void;
}) {
  const [minutes, setMinutes] = useState<number | undefined>(initial);
  const [km, setKm] = useState<number | undefined>();
  return (
    <Sheet open title="Log your walk" onClose={onClose}>
      <div className="space-y-5">
        <Stepper
          label="Minutes"
          unit="min"
          step={5}
          max={300}
          value={minutes}
          onChange={setMinutes}
        />
        <Stepper
          label="Distance (optional)"
          unit="km"
          step={0.1}
          decimals={2}
          max={50}
          value={km}
          onChange={setKm}
        />
        <Button
          size="lg"
          className="w-full"
          disabled={!minutes}
          onClick={() => minutes && onSave(minutes, km)}
        >
          Save walk
        </Button>
      </div>
    </Sheet>
  );
}

// ---------- Cool-down ----------

export function CooldownStep() {
  const { ctx, timer, setTimer, next } = useStepActions();
  const setSub = usePlayer((s) => s.setSub);
  const c = ctx.resolved.session.cooldown;
  const i = ctx.snap.sub["cooldown.index"] ?? 0;
  const stretch = c.stretches[i];
  const advance = () => (i + 1 < c.stretches.length ? setSub("cooldown.index", i + 1) : next());
  return (
    <div className="space-y-4">
      <StepTitle kicker="Cool-down" title={stretch ?? "Cool-down"} />
      <p className="text-muted">
        <RichText text={c.text} />
      </p>
      {stretch ? (
        <>
          <p className="font-semibold text-ink">
            Stretch {i + 1} of {c.stretches.length}
          </p>
          <CountdownRunner
            key={i}
            timer={timer}
            setTimer={setTimer}
            seconds={c.holdSec}
            label="Hold"
            onDone={advance}
            onSkip={advance}
            skipLabel={i + 1 < c.stretches.length ? "Next stretch" : "Finish"}
          />
        </>
      ) : (
        <Button size="lg" className="w-full" onClick={next}>
          Finish
        </Button>
      )}
      <ul className="space-y-1">
        {c.stretches.map((s, j) => (
          <li
            key={s}
            className={`flex items-center gap-2 text-sm ${j === i ? "font-semibold text-ink" : "text-muted"}`}
          >
            {j < i ? <Icon name="check" className="size-4" /> : <span className="size-4" />}
            {s}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function StepTitle({ kicker, title }: { kicker: string; title: string }) {
  return (
    <div>
      <p className="text-xs font-bold tracking-wide text-accent-ink uppercase">{kicker}</p>
      <h2 className="text-2xl leading-tight font-bold text-ink">{title}</h2>
    </div>
  );
}
