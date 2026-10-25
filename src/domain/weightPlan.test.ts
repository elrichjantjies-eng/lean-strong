import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import { onTrackBand, placeholderValues, targetKg, weeklyAverages, weightPlan } from "./weightPlan";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");
const MON = "2026-11-02";

describe("weight plan", () => {
  it("spreads the loss over the program, below the max pace", () => {
    const plan = weightPlan(gym, 90, 85)!;
    expect(plan.ratePerWeek).toBeCloseTo(5 / 12, 10);
    expect(plan.ratePerWeek.toFixed(1)).toBe("0.4");
    expect(plan.capped).toBe(false);
  });

  it("weeksToGoal uses the epsilon so float noise doesn't add a week", () => {
    // 64.4 − 52.4 = 12.000000000000007 kg at the capped 1 kg/week: lands just above 12.
    const toLose = 64.4 - 52.4;
    expect(Math.ceil(toLose / Math.min(toLose / 12, 1))).toBe(13); // the naive result
    expect(weightPlan(gym, 64.4, 52.4)!.weeksToGoal).toBe(12);
    expect(weightPlan(gym, 90, 85)!.weeksToGoal).toBe(12);
  });

  it("caps at maxRateKgPerWeek and reports the week the goal is reached", () => {
    const plan = weightPlan(home, 80, 70)!;
    expect(plan.ratePerWeek).toBe(1);
    expect(plan.capped).toBe(true);
    expect(plan.weeksToGoal).toBe(10);
    expect(plan.beyondProgram).toBe(true);
  });

  it("target path never goes below the goal", () => {
    const plan = weightPlan(gym, 90, 85)!;
    expect(targetKg(plan, 0)).toBe(90);
    expect(targetKg(plan, 6)).toBeCloseTo(87.5, 10);
    expect(targetKg(plan, 12)).toBeCloseTo(85, 10);
    expect(targetKg(plan, 14)).toBe(85);
  });

  it("checkpoint = target(checkpointWeek) rounded to 0.5 kg", () => {
    expect(weightPlan(home, 80, 70)!.checkpointKg).toBe(74);
    expect(weightPlan(home, 78.5, 72)!.checkpointKg).toBe(74); // 78.5 − 6 × 0.722 = 74.17 → 74
    expect(weightPlan(home, 80.3, 70)!.checkpointKg).toBe(74.5); // 80.3 − 6 × 1 = 74.3 → 74.5
    expect(weightPlan(gym, 90, 85)!.checkpointKg).toBeNull();
  });

  it("hides the plan when maintaining or gaining", () => {
    expect(weightPlan(gym, 80, 80)).toBeNull();
    expect(weightPlan(gym, 80, 82)).toBeNull();
  });

  it("on-track band for week w", () => {
    const [low, high] = onTrackBand(gym, 90, 2);
    expect(low).toBeCloseTo(88.8, 10);
    expect(high).toBeCloseTo(89.4, 10);
    expect(onTrackBand(home, 80, 3)).toEqual([77, 78.5]);
  });

  it("fills placeholders from the plan", () => {
    expect(placeholderValues(home, 80, 70)).toEqual({
      startKg: 80,
      goalKg: 70,
      weeks: 9,
      ratePerWeek: "1.0",
      weeksToGoal: 10,
      checkpointWeek: 6,
      checkpointKg: 74,
    });
    expect(placeholderValues(gym, 80, 80)).toEqual({ startKg: 80, goalKg: 80, weeks: 12 });
  });
});

describe("weekly averages", () => {
  it("averages entries within each program week and leaves gaps empty", () => {
    const entries = [
      { date: "2026-11-02", kg: 90 },
      { date: "2026-11-04", kg: 89 },
      { date: "2026-11-08", kg: 88.5 }, // still week 1 (Sunday)
      { date: "2026-11-16", kg: 88 }, // week 3
      { date: "2026-10-30", kg: 95 }, // before the start: ignored
      { date: "2027-02-01", kg: 80 }, // after the end: ignored
    ];
    const avg = weeklyAverages(entries, MON, 12);
    expect(avg).toHaveLength(12);
    expect(avg[0]).toBeCloseTo(89.1667, 4);
    expect(avg[1]).toBeNull();
    expect(avg[2]).toBe(88);
    expect(avg.slice(3).every((v) => v === null)).toBe(true);
  });
});
