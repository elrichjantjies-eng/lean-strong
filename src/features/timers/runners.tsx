import { useEffect, useRef } from "react";
import type { Interval } from "@/content/types";
import { useSetting } from "@/db/settings";
import { beep } from "@/platform/audio";
import { haptic } from "@/platform";
import { Button } from "@/components/ui";
import {
  adjust,
  displaySeconds,
  elapsedMs,
  formatClock,
  intervalPhases,
  intervalPosition,
  isRunning,
  newTimer,
  pause,
  remainingMs,
  start,
  totalMs,
  type TimerState,
} from "./engine";
import { TimerRing } from "./TimerDisplay";
import { useCountdownCues, useNow } from "./useTimer";

interface TimerProps {
  timer: TimerState | null;
  setTimer: (t: TimerState | null) => void;
}

/**
 * Countdown with big digits and a ring. `autoStart` starts it on mount (rest, switch);
 * otherwise a Start button is shown (holds, stretches).
 */
export function CountdownRunner({
  timer,
  setTimer,
  seconds,
  autoStart = false,
  adjustable = false,
  color = "var(--app-accent)",
  label,
  onDone,
  onSkip,
  skipLabel = "Skip",
  children,
}: TimerProps & {
  seconds: number;
  autoStart?: boolean;
  adjustable?: boolean;
  color?: string;
  label: string;
  onDone: () => void;
  onSkip?: () => void;
  skipLabel?: string;
  children?: React.ReactNode;
}) {
  const t = timer ?? newTimer(seconds * 1000);
  const running = isRunning(t);
  const now = useNow(running);

  useEffect(() => {
    if (!timer && autoStart) setTimer(start(newTimer(seconds * 1000), Date.now()));
  }, [timer, autoStart, seconds, setTimer]);

  useCountdownCues(t, now, onDone);
  const left = remainingMs(t, now);
  const fraction = t.durationMs ? left / t.durationMs : 0;

  return (
    <div className="space-y-5">
      <TimerRing fraction={fraction} color={color}>
        <span className="text-sm font-bold tracking-wide text-muted uppercase">{label}</span>
        <span
          className="text-7xl font-bold text-ink tabular-nums"
          role="timer"
          aria-label={`${label} time left`}
        >
          {formatClock(displaySeconds(left))}
        </span>
        {children}
      </TimerRing>
      <div className="flex gap-2">
        {adjustable && (
          <Button
            variant="secondary"
            size="lg"
            className="flex-1"
            aria-label="Remove 15 seconds"
            onClick={() => setTimer(adjust(t, -15_000, Date.now()))}
          >
            −15 s
          </Button>
        )}
        {!running && elapsedMs(t, now) === 0 ? (
          <Button size="lg" className="flex-[2]" onClick={() => setTimer(start(t, Date.now()))}>
            Start
          </Button>
        ) : !running ? (
          <Button size="lg" className="flex-[2]" onClick={() => setTimer(start(t, Date.now()))}>
            Resume
          </Button>
        ) : (
          !adjustable && (
            <Button
              variant="secondary"
              size="lg"
              className="flex-[2]"
              onClick={() => setTimer(pause(t, Date.now()))}
            >
              Pause
            </Button>
          )
        )}
        {adjustable && (
          <Button
            variant="secondary"
            size="lg"
            className="flex-1"
            aria-label="Add 15 seconds"
            onClick={() => setTimer(adjust(t, 15_000, Date.now()))}
          >
            +15 s
          </Button>
        )}
      </div>
      {onSkip && (
        <Button
          variant={adjustable ? "primary" : "ghost"}
          size="lg"
          className="w-full"
          onClick={onSkip}
        >
          {skipLabel}
        </Button>
      )}
    </div>
  );
}

/** Work/rest interval timer (rope warm-up, rope intervals, bike sprints). */
export function IntervalRunner({
  timer,
  setTimer,
  spec,
  color = "var(--app-accent)",
  onComplete,
  onSkip,
}: TimerProps & {
  spec: Interval;
  color?: string;
  onComplete: () => void;
  onSkip: () => void;
}) {
  const phases = intervalPhases(spec);
  const total = totalMs(phases);
  const t = timer ?? newTimer(total);
  const running = isRunning(t);
  const now = useNow(running);
  const pos = intervalPosition(phases, elapsedMs(t, now));
  const secs = displaySeconds(pos.phaseRemainingMs);
  const sound = useSetting("sound") ?? true;
  const haptics = useSetting("haptics") ?? true;

  // Cues: 3-2-1 at the end of each phase, a long beep when a new phase starts.
  const last = useRef<{ index: number; secs: number } | null>(null);
  const completed = useRef(false);
  const completeRef = useRef(onComplete);
  useEffect(() => {
    completeRef.current = onComplete;
  });
  useEffect(() => {
    if (!running) {
      last.current = null;
      return;
    }
    const prev = last.current;
    last.current = { index: pos.index, secs };
    if (pos.done) {
      if (!completed.current) {
        completed.current = true;
        if (prev && sound) beep("go");
        if (prev && haptics) void haptic("done");
        completeRef.current();
      }
      return;
    }
    if (!prev) return;
    if (pos.index !== prev.index) {
      if (sound) beep("go");
      if (haptics) void haptic("tick");
    } else if (secs !== prev.secs && secs >= 1 && secs <= 3) {
      if (sound) beep("tick");
    }
  }, [running, pos.index, pos.done, secs, sound, haptics]);

  const phase = pos.phase;
  const isWork = phase?.kind !== "rest";
  return (
    <div className="space-y-5">
      <TimerRing
        fraction={phase ? pos.phaseRemainingMs / phase.ms : 0}
        color={isWork ? color : "var(--app-muted)"}
      >
        <span
          className={`text-lg font-bold tracking-wide uppercase ${isWork ? "text-accent-ink" : "text-muted"}`}
        >
          {pos.done ? "Done" : isWork ? spec.activity : "Rest"}
        </span>
        <span
          className="text-7xl font-bold text-ink tabular-nums"
          role="timer"
          aria-label="Interval time left"
        >
          {formatClock(secs)}
        </span>
        <span className="mt-1 font-semibold text-muted">
          Round {Math.min(phase?.round ?? spec.rounds, spec.rounds)} of {spec.rounds}
        </span>
      </TimerRing>
      <p className="text-center text-sm text-muted">
        {spec.rounds} × {spec.workSec} s{spec.restSec ? ` / ${spec.restSec} s rest` : ""} ·{" "}
        {formatClock(total / 1000)} total
      </p>
      <div className="flex gap-2">
        {!running ? (
          <Button size="lg" className="flex-1" onClick={() => setTimer(start(t, Date.now()))}>
            {elapsedMs(t, now) === 0 ? "Start" : "Resume"}
          </Button>
        ) : (
          <Button
            variant="secondary"
            size="lg"
            className="flex-1"
            onClick={() => setTimer(pause(t, Date.now()))}
          >
            Pause
          </Button>
        )}
        <Button variant="ghost" size="lg" className="flex-1" onClick={onSkip}>
          {elapsedMs(t, now) > 0 ? "Finish early" : "Skip"}
        </Button>
      </div>
    </div>
  );
}

/** Stopwatch with a target (walks, "brisk walk 30–45 min"). */
export function StopwatchRunner({
  timer,
  setTimer,
  targetMin,
  label,
  color = "var(--app-accent)",
  onFinish,
}: TimerProps & {
  targetMin: [number, number];
  label: string;
  color?: string;
  onFinish: (elapsedMinutes: number) => void;
}) {
  const t = timer ?? newTimer(null);
  const running = isRunning(t);
  const now = useNow(running, 500);
  const elapsed = elapsedMs(t, now);
  const [lo, hi] = targetMin;
  const reached = elapsed >= lo * 60_000;
  const wasReached = useRef(reached);
  const sound = useSetting("sound") ?? true;
  useEffect(() => {
    if (reached && !wasReached.current && running && sound) beep("go");
    wasReached.current = reached;
  }, [reached, running, sound]);

  return (
    <div className="space-y-5">
      <TimerRing fraction={elapsed / (hi * 60_000)} color={color}>
        <span className="text-sm font-bold tracking-wide text-muted uppercase">{label}</span>
        <span
          className="text-6xl font-bold text-ink tabular-nums"
          role="timer"
          aria-label={`${label} time`}
        >
          {formatClock(Math.floor(elapsed / 1000))}
        </span>
        <span className="mt-1 font-semibold text-muted">
          Target {lo === hi ? lo : `${lo}–${hi}`} min{reached ? " ✓" : ""}
        </span>
      </TimerRing>
      <div className="flex gap-2">
        {!running ? (
          <Button size="lg" className="flex-1" onClick={() => setTimer(start(t, Date.now()))}>
            {elapsed === 0 ? "Start" : "Resume"}
          </Button>
        ) : (
          <Button
            variant="secondary"
            size="lg"
            className="flex-1"
            onClick={() => setTimer(pause(t, Date.now()))}
          >
            Pause
          </Button>
        )}
        <Button
          variant={reached ? "primary" : "secondary"}
          size="lg"
          className="flex-1"
          onClick={() => onFinish(Math.round(elapsed / 60_000))}
        >
          Finish
        </Button>
      </div>
    </div>
  );
}
