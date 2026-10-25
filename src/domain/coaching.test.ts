import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import { computeCheckin, isCheckinWeek, selectBand, UNLOCK_MESSAGE } from "./coaching";
import { proteinStatus, stepTarget, stepsStatus, weeklyDailyAverage } from "./daily";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");

describe("check-in schedule", () => {
  it("gym-12wk reviews in weeks 3, 5, 7…", () => {
    expect([1, 2, 3, 4, 5, 6, 7, 11].filter((w) => isCheckinWeek(gym, w))).toEqual([3, 5, 7, 11]);
  });
  it("home-9wk reviews every week from week 3", () => {
    expect([1, 2, 3, 4, 5, 9].filter((w) => isCheckinWeek(home, w))).toEqual([3, 4, 5, 9]);
  });
});

describe("band selection at exact boundaries (min inclusive, max exclusive)", () => {
  const status = (rate: number) => {
    const b = selectBand(gym.coaching.bands, rate)!;
    return `${b.status}:${b.minKgPerWeek ?? "-"}`;
  };
  it("gym-12wk", () => {
    expect(status(-0.5)).toBe("stalled:-");
    expect(status(0.0999)).toBe("stalled:-");
    expect(status(0.1)).toBe("slow:0.1");
    expect(status(0.3)).toBe("on-track:0.3");
    expect(status(0.6999)).toBe("on-track:0.3");
    expect(status(0.7)).toBe("on-track:0.7");
    expect(status(1.0)).toBe("fast:1");
    expect(status(5)).toBe("fast:1");
  });
  it("home-9wk", () => {
    const s = (rate: number) => selectBand(home.coaching.bands, rate)!.status;
    expect([0.29, 0.3, 0.5, 1.0, 1.3].map(s)).toEqual([
      "stalled",
      "slow",
      "on-track",
      "on-track",
      "fast",
    ]);
  });
});

describe("check-in rate", () => {
  it("week 3 compares weeks 1 and 2", () => {
    const r = computeCheckin(gym, 3, [90, 89.4]);
    expect(r.status).toBe("on-track");
    if (r.status === "locked") throw new Error();
    expect(r.rate).toBeCloseTo(0.6, 10);
    expect([r.fromWeek, r.toWeek]).toEqual([1, 2]);
    expect(r.message).toContain("perfect, change nothing");
  });

  it("from week 4 on it's a 2-week window: a = w − 3, b = w − 1", () => {
    const r = computeCheckin(gym, 5, [90, 89.5, 89, 88.2]);
    if (r.status === "locked") throw new Error();
    expect([r.fromWeek, r.toWeek]).toEqual([2, 4]);
    expect(r.rate).toBeCloseTo(0.65, 10);
  });

  it("uses the nearest week with data inside the window", () => {
    const noB = computeCheckin(gym, 5, [90, 89.5, 89, null]);
    if (noB.status === "locked") throw new Error();
    expect([noB.fromWeek, noB.toWeek]).toEqual([2, 3]);
    expect(noB.rate).toBeCloseTo(0.5, 10);
    const noA = computeCheckin(gym, 5, [90, null, 89, 88]);
    if (noA.status === "locked") throw new Error();
    expect([noA.fromWeek, noA.toWeek]).toEqual([3, 4]);
  });

  it("locks with fewer than minWeeksOfData weekly averages", () => {
    expect(computeCheckin(gym, 3, [null, 89])).toEqual({
      status: "locked",
      message: UNLOCK_MESSAGE,
    });
    expect(computeCheckin(gym, 5, [90, null, null, 88])).toEqual({
      status: "locked",
      message: UNLOCK_MESSAGE,
    });
  });

  it("flags calories below the floor (home-9wk)", () => {
    const low = computeCheckin(home, 3, [80, 79.2], 1100);
    expect(low.status !== "locked" && low.calorieWarning).toBe(true);
    const ok = computeCheckin(home, 3, [80, 79.2], 1400);
    expect(ok.status !== "locked" && ok.calorieWarning).toBe(false);
    const gymNoFloor = computeCheckin(gym, 3, [80, 79.2], 900);
    expect(gymNoFloor.status !== "locked" && gymNoFloor.calorieWarning).toBe(false);
  });
});

describe("steps and daily numbers", () => {
  const MON = "2026-11-02";
  const entry = (date: string, steps?: number, proteinG?: number) => ({
    id: date,
    profileId: "p1",
    date,
    ...(steps !== undefined ? { steps } : {}),
    ...(proteinG !== undefined ? { proteinG } : {}),
  });

  it("averages logged days in a week and compares with the week's target", () => {
    const entries = [
      entry("2026-11-02", 6000),
      entry("2026-11-03", 8000),
      entry("2026-11-04", undefined, 150),
    ];
    const avg = weeklyDailyAverage(entries, MON, 1, "steps");
    expect(avg).toBe(7000);
    const target = stepTarget(home, 1);
    expect(target).toBe(home.tracking.steps.dailyTargetByWeek[0]);
    expect(stepsStatus(avg, 6000)).toMatchObject({ met: true });
    expect(stepsStatus(null, 6000)).toBeNull();
    expect(weeklyDailyAverage(entries, MON, 2, "steps")).toBeNull();
  });

  it("compares protein with the program range", () => {
    expect(proteinStatus(150, gym.weightPlan.proteinGPerDay)).toBe("below");
    expect(proteinStatus(160, gym.weightPlan.proteinGPerDay)).toBe("in-range");
    expect(proteinStatus(200, gym.weightPlan.proteinGPerDay)).toBe("above");
  });
});
