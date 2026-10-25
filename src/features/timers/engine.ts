/**
 * Timer engine shared by rest, intervals, holds, walks and stretches. Time is always computed
 * from timestamps (never by counting ticks), so it stays right when the screen locks or the
 * app is in the background.
 */
import type { Interval } from "@/content/types";

export interface TimerState {
  /** Epoch ms when the timer was last (re)started; null while paused or not started. */
  startedAt: number | null;
  /** Elapsed ms accumulated before the last start. */
  accumulatedMs: number;
  /** Countdown length in ms; null for a stopwatch. */
  durationMs: number | null;
}

export const newTimer = (durationMs: number | null): TimerState => ({
  startedAt: null,
  accumulatedMs: 0,
  durationMs,
});

export function elapsedMs(t: TimerState, now: number): number {
  return t.accumulatedMs + (t.startedAt !== null ? Math.max(0, now - t.startedAt) : 0);
}

export function remainingMs(t: TimerState, now: number): number {
  return t.durationMs === null ? Infinity : Math.max(0, t.durationMs - elapsedMs(t, now));
}

export const isRunning = (t: TimerState) => t.startedAt !== null;
export const isDone = (t: TimerState, now: number) =>
  t.durationMs !== null && elapsedMs(t, now) >= t.durationMs;

export function start(t: TimerState, now: number): TimerState {
  return t.startedAt !== null ? t : { ...t, startedAt: now };
}

export function pause(t: TimerState, now: number): TimerState {
  return t.startedAt === null ? t : { ...t, accumulatedMs: elapsedMs(t, now), startedAt: null };
}

/** Add (or with a negative value, remove) time from a countdown, never below what's elapsed. */
export function adjust(t: TimerState, deltaMs: number, now: number): TimerState {
  if (t.durationMs === null) return t;
  return { ...t, durationMs: Math.max(elapsedMs(t, now), t.durationMs + deltaMs) };
}

/** When a running countdown will hit zero (epoch ms), for background notifications. */
export function endsAt(t: TimerState, now: number): number | null {
  if (t.startedAt === null || t.durationMs === null) return null;
  return now + remainingMs(t, now);
}

/** Whole seconds shown on a countdown (ceil, so "3" shows until the last second starts). */
export function displaySeconds(ms: number): number {
  return Math.max(0, Math.ceil(ms / 1000 - 1e-9));
}

export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.round(totalSeconds));
  const m = Math.floor(s / 60);
  const h = Math.floor(m / 60);
  const ss = String(s % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m % 60).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
}

export interface IntervalPhase {
  kind: "work" | "rest";
  round: number; // 1-based
  ms: number;
}

/** Expand an interval spec into work/rest phases (no trailing rest after the last round). */
export function intervalPhases(spec: Interval): IntervalPhase[] {
  const phases: IntervalPhase[] = [];
  for (let r = 1; r <= spec.rounds; r++) {
    phases.push({ kind: "work", round: r, ms: spec.workSec * 1000 });
    if (spec.restSec > 0 && r < spec.rounds) {
      phases.push({ kind: "rest", round: r, ms: spec.restSec * 1000 });
    }
  }
  return phases;
}

export const totalMs = (phases: IntervalPhase[]) => phases.reduce((s, p) => s + p.ms, 0);

export interface IntervalPosition {
  index: number;
  phase: IntervalPhase | null;
  phaseRemainingMs: number;
  done: boolean;
}

/** Where we are in a list of phases after `elapsed` ms. */
export function intervalPosition(phases: IntervalPhase[], elapsed: number): IntervalPosition {
  let t = elapsed;
  for (let i = 0; i < phases.length; i++) {
    const p = phases[i]!;
    if (t < p.ms) return { index: i, phase: p, phaseRemainingMs: p.ms - t, done: false };
    t -= p.ms;
  }
  return { index: phases.length, phase: null, phaseRemainingMs: 0, done: true };
}
