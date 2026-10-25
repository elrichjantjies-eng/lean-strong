import { describe, expect, it } from "vitest";
import {
  adjust,
  displaySeconds,
  elapsedMs,
  endsAt,
  formatClock,
  intervalPhases,
  intervalPosition,
  isDone,
  newTimer,
  pause,
  remainingMs,
  start,
  totalMs,
} from "./engine";

describe("timer engine", () => {
  it("computes time from timestamps, so a locked screen doesn't drift", () => {
    let t = start(newTimer(90_000), 1_000);
    expect(remainingMs(t, 1_000)).toBe(90_000);
    // 60 s later with no ticks in between (screen locked):
    expect(remainingMs(t, 61_000)).toBe(30_000);
    t = pause(t, 61_000);
    expect(remainingMs(t, 500_000)).toBe(30_000); // paused: frozen
    t = start(t, 500_000);
    expect(remainingMs(t, 530_000)).toBe(0);
    expect(isDone(t, 530_000)).toBe(true);
  });

  it("adjusts by ±15 s but never below elapsed", () => {
    let t = start(newTimer(60_000), 0);
    t = adjust(t, 15_000, 10_000);
    expect(remainingMs(t, 10_000)).toBe(65_000);
    t = adjust(t, -100_000, 10_000);
    expect(remainingMs(t, 10_000)).toBe(0);
  });

  it("stopwatch has no end", () => {
    const t = start(newTimer(null), 0);
    expect(elapsedMs(t, 5_000)).toBe(5_000);
    expect(remainingMs(t, 5_000)).toBe(Infinity);
    expect(endsAt(t, 5_000)).toBeNull();
  });

  it("reports when a countdown ends, for background notifications", () => {
    const t = start(newTimer(90_000), 1_000);
    expect(endsAt(t, 31_000)).toBe(91_000);
    expect(endsAt(newTimer(90_000), 0)).toBeNull();
  });

  it("formats digits", () => {
    expect(displaySeconds(2_001)).toBe(3);
    expect(displaySeconds(3_000)).toBe(3);
    expect(displaySeconds(0)).toBe(0);
    expect(formatClock(90)).toBe("1:30");
    expect(formatClock(5)).toBe("0:05");
    expect(formatClock(3_725)).toBe("1:02:05");
  });
});

describe("interval timer", () => {
  const spec = { activity: "Skipping", rounds: 3, workSec: 30, restSec: 30 };

  it("expands rounds into work/rest with no trailing rest", () => {
    const phases = intervalPhases(spec);
    expect(phases.map((p) => `${p.kind}${p.round}`)).toEqual([
      "work1",
      "rest1",
      "work2",
      "rest2",
      "work3",
    ]);
    expect(totalMs(phases)).toBe(150_000);
    expect(intervalPhases({ ...spec, rounds: 1, workSec: 300, restSec: 0 })).toHaveLength(1);
  });

  it("finds the position from elapsed time", () => {
    const phases = intervalPhases(spec);
    expect(intervalPosition(phases, 0)).toMatchObject({ index: 0, phaseRemainingMs: 30_000 });
    expect(intervalPosition(phases, 45_000)).toMatchObject({
      index: 1,
      phase: { kind: "rest", round: 1 },
      phaseRemainingMs: 15_000,
    });
    expect(intervalPosition(phases, 150_000)).toMatchObject({ done: true });
  });
});
