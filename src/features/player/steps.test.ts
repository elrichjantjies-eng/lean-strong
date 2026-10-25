import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import { resolveSession } from "@/domain/session";
import { buildSteps, indexOfKey } from "./steps";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");

describe("player steps", () => {
  it("orders power sets A1 → switch → A2 → rest → A1 …", () => {
    const steps = buildSteps(resolveSession(gym, "A", 1), {});
    const keys = steps.map((s) => s.key);
    expect(keys.slice(0, 4)).toEqual(["overview", "warmup-rope", "warmup-mobility", "warmup-sets"]);
    expect(keys.slice(4, 10)).toEqual([
      "set:0:0:0",
      "switch:0:0",
      "set:0:1:0",
      "rest:0:0",
      "set:0:0:1",
      "switch:0:1",
    ]);
    const sets = steps.filter((s) => s.type === "set");
    expect(sets).toHaveLength(24); // 4 pairs × 3 sets × 2 exercises in phase 1
    expect(keys.slice(-3)).toEqual(["finisher", "cooldown", "summary"]);
    // No rest right before the finisher.
    expect(steps[steps.length - 4]!.type).toBe("set");
  });

  it("rest uses the phase's rest seconds", () => {
    const rest = buildSteps(resolveSession(gym, "A", 9), {}).find((s) => s.type === "rest");
    expect(rest).toMatchObject({ seconds: 60 });
  });

  it("home sessions add the waist circuit and the walk", () => {
    const keys = buildSteps(resolveSession(home, "A", 1), {}).map((s) => s.key);
    expect(keys.slice(-4)).toEqual(["waist", "walk", "cooldown", "summary"]);
  });

  it("adds and removes sets, and skips exercises", () => {
    const resolved = resolveSession(gym, "A", 1);
    const plus = buildSteps(resolved, { A1: { sets: 4 } });
    expect(plus.filter((s) => s.type === "set" && s.pairIndex === 0)).toHaveLength(7);
    // A1's 4th set has no partner, so no switch in that round.
    expect(plus.map((s) => s.key)).toContain("set:0:0:3");
    expect(plus.map((s) => s.key)).not.toContain("switch:0:3");
    const skipped = buildSteps(resolved, { A2: { skipped: "Machine busy" } });
    expect(skipped.filter((s) => s.type === "set" && s.pairIndex === 0)).toHaveLength(3);
    expect(skipped.some((s) => s.type === "switch" && s.pairIndex === 0)).toBe(false);
  });

  it("finds the user's place after the list changes", () => {
    const steps = buildSteps(resolveSession(gym, "A", 1), {});
    expect(indexOfKey(steps, "set:0:1:0")).toBe(6);
    expect(indexOfKey(steps, "set:9:9:9", 6)).toBe(6);
  });
});
