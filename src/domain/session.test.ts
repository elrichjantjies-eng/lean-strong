import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import type { Target } from "@/content/types";
import { resolveSession, resolveTarget, walkForDay } from "./session";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");

describe("per-phase sets, rest and warm-up", () => {
  it("uses setsByPhase[p] and restSecondsByPhase[p]", () => {
    const [w1, w5, w9] = [1, 5, 9].map((w) => resolveSession(gym, "A", w)) as [
      ReturnType<typeof resolveSession>,
      ReturnType<typeof resolveSession>,
      ReturnType<typeof resolveSession>,
    ];
    const bench = (s: typeof w1) => s.pairs[0]!.items[0];
    expect([bench(w1).sets, bench(w5).sets, bench(w9).sets]).toEqual([3, 4, 4]);
    const fly = (s: typeof w1) => s.pairs[2]!.items[0];
    expect(fly(w1).exerciseId).toBe("cable_fly");
    expect([fly(w1).sets, fly(w5).sets, fly(w9).sets]).toEqual([3, 3, 4]);
    expect([w1.restSec, w5.restSec, w9.restSec]).toEqual([90, 75, 60]);
    expect([w1.rope.rounds, w5.rope.rounds, w9.rope.rounds]).toEqual([5, 6, 8]);
  });

  it("uses the session's own rope warm-up when it isn't 'phase'", () => {
    expect(resolveSession(gym, "H", 1).rope.workSec).toBe(300);
  });

  it("resolves the finisher for the phase", () => {
    const f = resolveSession(gym, "A", 9).finisher;
    expect(f).toMatchObject({
      kind: "intervals",
      interval: { rounds: 10, workSec: 40, restSec: 20 },
    });
    expect(resolveSession(gym, "B", 1).finisher).toMatchObject({ alternative: expect.any(String) });
    const c = resolveSession(gym, "C", 9).finisher;
    expect(c?.kind === "circuit" && c.circuit.rounds).toBe(4);
    expect(resolveSession(gym, "H", 1).finisher?.kind).toBe("choice");
  });

  it("estimates a plausible duration", () => {
    const min = resolveSession(gym, "A", 1).estimatedMinutes;
    expect(min).toBeGreaterThanOrEqual(40);
    expect(min).toBeLessThanOrEqual(90);
  });
});

describe("targets", () => {
  const base: Target = { kind: "reps", min: 8, max: 10, perSide: false, note: "base" };

  it("falls back to min/max and note", () => {
    expect(resolveTarget(base, 1)).toEqual({
      kind: "reps",
      min: 8,
      max: 10,
      perSide: false,
      note: "base",
    });
  });

  it("uses byPhase and noteByPhase when present", () => {
    const t: Target = {
      ...base,
      byPhase: [
        { min: 8, max: 10 },
        { min: 6, max: 8 },
        { min: 5, max: 6 },
      ],
      noteByPhase: [null, "slow", null],
    };
    expect(resolveTarget(t, 1)).toMatchObject({ min: 6, max: 8, note: "slow" });
    expect(resolveTarget(t, 2)).toMatchObject({ min: 5, max: 6, note: "base" });
  });

  it("applies the real per-phase content", () => {
    const climber = (w: number) => resolveSession(gym, "H", w).pairs[2]!.items[1];
    expect(climber(1).target).toMatchObject({ kind: "time", min: 30, max: 30 });
    expect(climber(5).target).toMatchObject({ min: 40, max: 40 });
    const squat = (w: number) => resolveSession(gym, "H", w).pairs[0]!.items[0].target.note;
    expect(squat(1)).toBeUndefined();
    expect(squat(9)).toBe("Pause 2 s at the bottom");
  });
});

describe("waist circuit and walks (home-9wk)", () => {
  it("uses roundsByPhase and adds the hold bonus from its week", () => {
    const at = (w: number) => resolveSession(home, "A", w).waistCircuit!;
    expect([at(3).rounds, at(4).rounds, at(9).rounds]).toEqual([2, 3, 3]);
    const plank = (w: number) => at(w).steps.find((s) => s.exerciseId === "plank")!.target;
    expect(plank(6)).toMatchObject({ min: 30, max: 45 });
    expect(plank(7)).toMatchObject({ min: 40, max: 55 });
    // Rep moves don't get the bonus.
    expect(at(7).steps[0]!.target).toMatchObject({ kind: "reps", min: 8, max: 8, perSide: true });
    expect(at(1).restSec).toBe(60);
  });

  it("walk after uses minutesByPhase", () => {
    expect([1, 4, 7].map((w) => resolveSession(home, "A", w).walkAfterMin)).toEqual([
      [25, 25],
      [30, 30],
      [35, 40],
    ]);
  });

  it("walk days add intervals from intervalsFromWeek", () => {
    const tue = home.weekTemplate[1]!;
    expect(walkForDay(home, tue, 3)).toEqual({ minutes: [45, 45], optional: false });
    expect(walkForDay(home, tue, 4)?.intervals?.briskSec).toBe(180);
    expect(walkForDay(home, tue, 7)?.minutes).toEqual([60, 60]);
    const sat = home.weekTemplate[5]!;
    expect(walkForDay(home, sat, 8)?.intervals).toBeUndefined();
    expect(walkForDay(home, home.weekTemplate[6]!, 1)?.optional).toBe(true);
    expect(walkForDay(gym, gym.weekTemplate[0]!, 1)).toBeNull();
  });
});
