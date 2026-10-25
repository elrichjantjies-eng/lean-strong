import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import { db } from "@/db/db";
import type { SetLog } from "@/domain/model";
import { phaseForWeek, programWeek } from "@/domain/schedule";
import { resolveSession } from "@/domain/session";
import {
  cancelTimerAlert,
  isIosBrowser,
  isNative,
  keepAwake,
  REQUEST_EXIT_EVENT,
  scheduleTimerAlert,
  wakeLockSupported,
} from "@/platform";
import { unlockAudio } from "@/platform/audio";
import { Icon } from "@/components/Icon";
import { RichText } from "@/components/RichText";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button, Callout, Card, ProgressBar } from "@/components/ui";
import { endsAt } from "@/features/timers/engine";
import { PlayerContext, stepSection, targetText, usePlayerCtx, type PlayerCtx } from "./context";
import {
  CooldownStep,
  FinisherStep,
  MobilityStep,
  RestStep,
  RopeStep,
  SwitchStep,
  WaistStep,
  WalkStep,
  WarmupSetsStep,
} from "./FlowSteps";
import { SetStep } from "./SetStep";
import { usePlayer } from "./store";
import { buildSteps, indexOfKey, type Step } from "./steps";
import { Summary } from "./Summary";

export function WorkoutScreen() {
  const { sessionId = "" } = useParams();
  const { profile, program } = useActive();
  const navigate = useNavigate();
  const today = useToday();
  const snap = usePlayer((s) => s.snap);
  const [loaded, setLoaded] = useState(false);
  const done = usePlayer((s) => s.finished);

  useEffect(() => {
    void usePlayer
      .getState()
      .load()
      .then(() => setLoaded(true));
  }, []);

  const currentWeek = Math.min(
    program.weeks,
    Math.max(1, programWeek(profile.startDate, today) ?? 1),
  );
  const week = snap?.week ?? currentWeek;
  const resolved = useMemo(
    () => (program.sessions[sessionId] ? resolveSession(program, sessionId, week) : null),
    [program, sessionId, week],
  );
  const steps = useMemo(
    () => (resolved ? buildSteps(resolved, snap?.mods ?? {}) : []),
    [resolved, snap?.mods],
  );
  const index = snap ? indexOfKey(steps, snap.stepKey, snap.stepIndex) : 0;
  const step = steps[index];
  const active = !!snap && snap.sessionId === sessionId && snap.profileId === profile.id;

  // Keep the screen awake for the whole session.
  useEffect(() => {
    if (!active) return;
    void keepAwake(true);
    return () => void keepAwake(false);
  }, [active]);

  // Android: if a countdown is running when the app goes to the background, notify at its end.
  useEffect(() => {
    if (!active || !isNative) return;
    const onVis = () => {
      const s = usePlayer.getState().snap;
      if (document.visibilityState === "hidden" && s?.timer) {
        const at = endsAt(s.timer, Date.now());
        if (at) void scheduleTimerAlert(at, "Time's up", "Your timer has finished. Back to it!");
      } else void cancelTimerAlert();
    };
    document.addEventListener("visibilitychange", onVis);
    return () => document.removeEventListener("visibilitychange", onVis);
  }, [active]);

  // Reaching the summary marks the session complete.
  useEffect(() => {
    if (active && step?.type === "summary") void usePlayer.getState().finish();
  }, [active, step?.type]);

  if (!resolved) {
    return (
      <PlayerFrame title="Workout" onExit={() => navigate("/")}>
        <p className="text-ink">This session isn't in your program.</p>
      </PlayerFrame>
    );
  }
  if (!loaded) return <div className="min-h-dvh bg-bg" />;

  if (done) {
    return (
      <PlayerFrame
        title={resolved.session.title}
        onExit={() => navigate("/", { replace: true })}
        plain
      >
        <Summary snap={done.snap} program={program} profile={profile} finishedAt={done.at} />
      </PlayerFrame>
    );
  }

  if (snap && !active) {
    const other = program.sessions[snap.sessionId];
    return (
      <PlayerFrame title={resolved.session.title} onExit={() => navigate("/")}>
        <Callout tone="accent" title="Another workout is in progress">
          <p>{other?.title ?? "A workout"} isn't finished yet.</p>
        </Callout>
        <div className="mt-4 space-y-2">
          <Button
            size="lg"
            className="w-full"
            onClick={() => navigate(`/workout/${snap.sessionId}`, { replace: true })}
          >
            Resume {other?.title}
          </Button>
          <Button
            variant="secondary"
            size="lg"
            className="w-full"
            onClick={() => void usePlayer.getState().abandon()}
          >
            End it and start {resolved.session.title}
          </Button>
        </div>
      </PlayerFrame>
    );
  }

  if (!snap) {
    return (
      <Overview
        resolved={resolved}
        onExit={() => navigate(-1)}
        onStart={async () => {
          unlockAudio(); // iOS: audio only plays after a user tap
          await usePlayer.getState().begin({
            profileId: profile.id,
            programId: program.id,
            sessionId,
            week,
            phase: phaseForWeek(program, week).number,
            date: today,
            firstKey: steps[1]!.key,
          });
        }}
      />
    );
  }

  const ctx: PlayerCtx = { program, profile, resolved, steps, index, snap };
  const progress = index / (steps.length - 1);
  return (
    <PlayerContext.Provider value={ctx}>
      <ActivePlayer ctx={ctx} step={step!} progress={progress} />
    </PlayerContext.Provider>
  );
}

function ActivePlayer({ ctx, step, progress }: { ctx: PlayerCtx; step: Step; progress: number }) {
  const navigate = useNavigate();
  const [sheet, setSheet] = useState<null | "exit" | "note" | "sets">(null);
  const back = usePlayer((s) => s.back);
  const awakeOk = wakeLockSupported();

  // Android back button: ask before leaving the workout.
  useEffect(() => {
    const ask = () => setSheet("exit");
    window.addEventListener(REQUEST_EXIT_EVENT, ask);
    return () => window.removeEventListener(REQUEST_EXIT_EVENT, ask);
  }, []);

  return (
    <PlayerFrame
      title={stepSection(step, ctx)}
      progress={progress}
      onExit={() => setSheet("exit")}
      actions={
        <>
          <TopButton label="Logged sets" icon="exercises" onClick={() => setSheet("sets")} />
          <TopButton label="Add a note" icon="plus" onClick={() => setSheet("note")} />
        </>
      }
    >
      <div key={step.key}>
        <StepView step={step} />
      </div>
      {ctx.index > 1 && (
        <button
          type="button"
          onClick={() => back(ctx.steps)}
          className="mt-6 block min-h-12 w-full text-center text-sm font-semibold text-muted"
        >
          ← Previous step
        </button>
      )}
      {!awakeOk && isIosBrowser() && step.type === "rope" && (
        <p className="mt-4 text-center text-sm text-muted">
          Tip: your phone may lock during rests. Set Settings → Display & Brightness → Auto-Lock to
          a longer time while you train.
        </p>
      )}

      <Sheet open={sheet === "exit"} title="Leave the workout?" onClose={() => setSheet(null)}>
        <p className="text-ink">
          Your progress is saved. You can pick up where you left off from Today.
        </p>
        <div className="mt-5 space-y-2">
          <Button size="lg" className="w-full" onClick={() => setSheet(null)}>
            Keep going
          </Button>
          <Button
            variant="secondary"
            size="lg"
            className="w-full"
            onClick={() => navigate("/", { replace: true })}
          >
            Leave for now
          </Button>
          <Button
            variant="ghost"
            className="w-full"
            onClick={async () => {
              await usePlayer.getState().abandon();
              navigate("/", { replace: true });
            }}
          >
            End without finishing
          </Button>
        </div>
      </Sheet>
      {sheet === "note" && <NoteSheet onClose={() => setSheet(null)} />}
      {sheet === "sets" && <LoggedSetsSheet onClose={() => setSheet(null)} />}
    </PlayerFrame>
  );
}

function StepView({ step }: { step: Step }) {
  switch (step.type) {
    case "overview":
      return null;
    case "rope":
      return <RopeStep />;
    case "mobility":
      return <MobilityStep />;
    case "warmupSets":
      return <WarmupSetsStep />;
    case "set":
      return <SetStep step={step} />;
    case "switch":
      return <SwitchStep step={step} />;
    case "rest":
      return <RestStep step={step} />;
    case "finisher":
      return <FinisherStep />;
    case "waist":
      return <WaistStep />;
    case "walk":
      return <WalkStep />;
    case "cooldown":
      return <CooldownStep />;
    case "summary":
      return null;
  }
}

function PlayerFrame({
  title,
  progress,
  onExit,
  actions,
  plain,
  children,
}: {
  title: string;
  progress?: number;
  onExit: () => void;
  actions?: React.ReactNode;
  plain?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-dvh bg-bg">
      <header
        className="sticky top-0 z-20 border-b border-line bg-bg/95 backdrop-blur"
        style={{ paddingTop: "var(--safe-top)" }}
      >
        <div className="mx-auto flex h-14 max-w-xl items-center gap-1 px-2">
          <TopButton label={plain ? "Close" : "Exit workout"} icon="close" onClick={onExit} />
          <p className="flex-1 truncate text-center font-semibold text-ink">{title}</p>
          {actions ?? <span className="w-12" />}
        </div>
        {progress !== undefined && (
          <div className="mx-auto max-w-xl px-4 pb-2">
            <ProgressBar value={progress} label="Workout progress" />
          </div>
        )}
      </header>
      <main
        className="mx-auto max-w-xl px-4 pt-4"
        style={{ paddingBottom: "calc(var(--safe-bottom) + 2rem)" }}
      >
        {children}
      </main>
    </div>
  );
}

function TopButton({
  label,
  icon,
  onClick,
}: {
  label: string;
  icon: "close" | "plus" | "exercises";
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className="flex size-12 shrink-0 items-center justify-center rounded-full text-ink active:bg-surface-2"
    >
      <Icon name={icon} />
    </button>
  );
}

function Overview({
  resolved,
  onStart,
  onExit,
}: {
  resolved: NonNullable<ReturnType<typeof resolveSession>>;
  onStart: () => Promise<void>;
  onExit: () => void;
}) {
  const { program } = useActive();
  const s = resolved.session;
  const color = program.theme.dayColors[s.id] ?? s.color;
  const [starting, setStarting] = useState(false);
  return (
    <PlayerFrame title="Overview" onExit={onExit}>
      <div className="space-y-4">
        <div className="-mx-4 -mt-4 px-4 pt-4 pb-5 text-white" style={{ background: color }}>
          <p className="text-xs font-bold tracking-wide uppercase opacity-90">
            Week {resolved.week} · Phase {resolved.p + 1}
          </p>
          <h1 className="text-3xl font-bold">{s.title}</h1>
          <p className="font-semibold">{s.focus}</p>
        </div>
        <p className="text-ink">
          <RichText text={s.intro} />
        </p>
        <p className="font-semibold text-ink">
          About {resolved.estimatedMinutes} min · rest {resolved.restSec} s after each pair
        </p>
        <Card className="py-1">
          <ul>
            {resolved.pairs.flatMap((pair) =>
              pair.items.map((item) => {
                const ex = program.exercises[item.exerciseId]!;
                return (
                  <li
                    key={item.code}
                    className="flex items-center gap-3 border-b border-line py-2.5 last:border-0"
                  >
                    <span className="w-8 font-bold" style={{ color }}>
                      {item.code}
                    </span>
                    <span className="flex-1">
                      <span className="block font-semibold text-ink">{ex.name}</span>
                      <span className="block text-sm text-muted">
                        {item.sets} × {targetText(item.target)}
                      </span>
                    </span>
                  </li>
                );
              }),
            )}
          </ul>
        </Card>
        {resolved.finisher && (
          <p className="text-ink">
            <span className="font-semibold">Finisher: </span>
            {resolved.finisher.name}
          </p>
        )}
        {resolved.waistCircuit && (
          <p className="text-ink">
            <span className="font-semibold">{resolved.waistCircuit.title}: </span>
            {resolved.waistCircuit.rounds} rounds
          </p>
        )}
        <Button
          size="lg"
          className="w-full text-xl"
          disabled={starting}
          onClick={async () => {
            setStarting(true);
            await onStart();
          }}
        >
          <Icon name="play" className="size-5" /> Start
        </Button>
      </div>
    </PlayerFrame>
  );
}

function NoteSheet({ onClose }: { onClose: () => void }) {
  const notes = usePlayer((s) => s.snap?.notes ?? "");
  const update = usePlayer((s) => s.update);
  const [text, setText] = useState(notes);
  return (
    <Sheet open title="Workout note" onClose={onClose}>
      <textarea
        aria-label="Note"
        value={text}
        rows={5}
        onChange={(e) => setText(e.target.value)}
        className="w-full rounded-xl bg-surface p-3 text-ink ring-1 ring-line focus:ring-2 focus:ring-[var(--app-accent)] focus:outline-none"
      />
      <Button
        size="lg"
        className="mt-4 w-full"
        onClick={() => {
          update((s) => ({ ...s, notes: text }));
          onClose();
        }}
      >
        Save note
      </Button>
    </Sheet>
  );
}

function LoggedSetsSheet({ onClose }: { onClose: () => void }) {
  const { program, snap } = usePlayerCtx();
  const sets = useLiveQuery(
    () => db.setLogs.where("sessionLogId").equals(snap.sessionLogId).sortBy("completedAt"),
    [snap.sessionLogId],
  );
  const [editing, setEditing] = useState<SetLog | null>(null);
  return (
    <Sheet open title="Logged sets" onClose={onClose}>
      {!sets?.length && <p className="text-muted">Nothing logged yet.</p>}
      <ul className="divide-y divide-line rounded-xl bg-surface ring-1 ring-line">
        {sets?.map((s) => (
          <li key={s.id}>
            <button
              type="button"
              onClick={() => setEditing(s)}
              className="flex min-h-12 w-full items-center justify-between px-4 py-2 text-left"
            >
              <span className="text-ink">
                <span className="font-semibold">{s.pairCode}</span>{" "}
                {program.exercises[s.exerciseId]?.name} · set {s.setIndex + 1}
              </span>
              <span className="text-muted tabular-nums">
                {s.weightKg !== undefined ? `${s.weightKg} kg × ` : ""}
                {s.reps !== undefined ? s.reps : s.seconds !== undefined ? `${s.seconds} s` : ""}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {editing && <EditSet key={editing.id} set={editing} onDone={() => setEditing(null)} />}
    </Sheet>
  );
}

function EditSet({ set, onDone }: { set: SetLog; onDone: () => void }) {
  const [weightKg, setWeight] = useState(set.weightKg);
  const [reps, setReps] = useState(set.reps);
  const [seconds, setSeconds] = useState(set.seconds);
  return (
    <div className="mt-4 space-y-3 rounded-xl bg-surface-2 p-3">
      {set.weightKg !== undefined && (
        <Stepper
          label="Weight"
          unit="kg"
          step={0.5}
          decimals={2}
          max={500}
          value={weightKg}
          onChange={setWeight}
        />
      )}
      {set.reps !== undefined && <Stepper label="Reps" value={reps} onChange={setReps} max={500} />}
      {set.seconds !== undefined && (
        <Stepper label="Seconds" value={seconds} onChange={setSeconds} max={600} step={5} />
      )}
      <Button
        className="w-full"
        onClick={async () => {
          await db.setLogs.update(set.id, {
            ...(weightKg !== undefined ? { weightKg } : {}),
            ...(reps !== undefined ? { reps } : {}),
            ...(seconds !== undefined ? { seconds } : {}),
          });
          onDone();
        }}
      >
        Save changes
      </Button>
    </div>
  );
}
