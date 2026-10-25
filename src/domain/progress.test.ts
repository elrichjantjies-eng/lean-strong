import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import { sessionLog, setLog } from "@/test/factories";
import type { MeasurementEntry } from "./model";
import { measurementTrend, strengthCheckins, waistFieldId, weightChartData } from "./progress";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");
const MON = "2026-11-02";
const w = (date: string, kg: number) => ({ id: date, profileId: "p", date, kg });

describe("weight chart data (hand-checked example)", () => {
  // home-9wk, 80 → 72 kg: rate = min(8/9, 1) = 0.888… kg/week, band 0.5–1.0 kg/week,
  // checkpoint week 6 = 80 − 6 × 0.888… = 74.67 → 74.5 kg.
  const entries = [
    w("2026-11-02", 80.0), // week 1
    w("2026-11-05", 79.4),
    w("2026-11-09", 79.0), // week 2
    w("2026-11-12", 78.6),
    // week 3: no entries
    w("2026-11-23", 77.4), // week 4
  ];
  const data = weightChartData(home, { startDate: MON, startKg: 80, goalKg: 72 }, entries);

  it("plots daily entries in weeks since the start", () => {
    expect(data.daily.map((p) => [Number(p.x.toFixed(3)), p.y])).toEqual([
      [0, 80],
      [0.429, 79.4],
      [1, 79],
      [1.429, 78.6],
      [3, 77.4],
    ]);
  });

  it("weekly averages mid-week, gaps stay empty", () => {
    expect(
      data.weekly.slice(0, 5).map((p) => [p.x, p.y === null ? null : Number(p.y.toFixed(2))]),
    ).toEqual([
      [0.5, 79.7],
      [1.5, 78.8],
      [2.5, null],
      [3.5, 77.4],
      [4.5, null],
    ]);
  });

  it("target path, band, goal and checkpoint", () => {
    expect(data.target[0]).toEqual({ x: 0, y: 80 });
    expect(data.target[9]!.y).toBeCloseTo(72, 10);
    expect(data.target[4]!.y).toBeCloseTo(76.444, 3);
    expect(data.band[4]).toEqual({ x: 4, lo: 76, hi: 78 });
    expect(data.goalKg).toBe(72);
    expect(data.checkpoint).toEqual({ x: 6, y: 74.5 });
  });

  it("table rows, lost so far and pace status", () => {
    const row4 = data.rows[3]!;
    expect(row4.average).toBe(77.4);
    expect(row4.diff).toBeCloseTo(77.4 - 76.444, 3);
    expect(data.latestWeek).toBe(4);
    expect(data.lostSoFar).toBeCloseTo(2.6, 10);
    // Week 4 band is 76–78 and the average 77.4 sits inside it.
    expect(data.status).toBe("on-track");
  });

  it("ahead / behind relative to the band", () => {
    const fast = weightChartData(home, { startDate: MON, startKg: 80, goalKg: 72 }, [
      w("2026-11-09", 77.5),
    ]);
    expect(fast.status).toBe("ahead"); // week 2 band: 78–79
    const slow = weightChartData(home, { startDate: MON, startKg: 80, goalKg: 72 }, [
      w("2026-11-09", 79.5),
    ]);
    expect(slow.status).toBe("behind");
  });

  it("hides path and band when maintaining or gaining", () => {
    const gain = weightChartData(gym, { startDate: MON, startKg: 70, goalKg: 75 }, entries);
    expect(gain.target).toEqual([]);
    expect(gain.band).toEqual([]);
    expect(gain.status).toBeNull();
    expect(gain.checkpoint).toBeNull();
  });
});

describe("measurements", () => {
  it("finds the waist field and its trend", () => {
    expect(waistFieldId(home)).toBe("waistNavel");
    expect(waistFieldId(gym)).toBe("waist");
    const entries: MeasurementEntry[] = [
      {
        id: "1",
        profileId: "p",
        date: "2026-11-02",
        week: 1,
        values: { waistNavel: 92, hips: 104 },
      },
      { id: "2", profileId: "p", date: "2026-11-09", week: 2, values: { waistNavel: 91.2 } },
      { id: "3", profileId: "p", date: "2026-11-16", week: 3, values: { waistNavel: 90.5 } },
    ];
    const t = measurementTrend(entries, "waistNavel")!;
    expect(t.change).toBeCloseTo(-1.5, 10);
    expect(t.sincePrevious).toBeCloseTo(-0.7, 10);
    expect(measurementTrend(entries, "hips")?.sincePrevious).toBeNull();
    expect(measurementTrend(entries, "thigh")).toBeNull();
  });
});

describe("strength check-ins", () => {
  it("fills the best set per listed week from completed logs", () => {
    const logs = [
      sessionLog({ id: "w1", week: 1 }),
      sessionLog({ id: "w4", week: 4 }),
      sessionLog({ id: "w4x", week: 4, status: "in-progress" }),
    ];
    const sets = [
      setLog({ sessionLogId: "w1", exerciseId: "bench_press", weightKg: 60, reps: 10 }),
      setLog({ sessionLogId: "w4", exerciseId: "bench_press", weightKg: 65, reps: 8 }),
      setLog({ sessionLogId: "w4", exerciseId: "bench_press", weightKg: 62.5, reps: 10 }),
      setLog({ sessionLogId: "w4x", exerciseId: "bench_press", weightKg: 100, reps: 10 }),
    ];
    const rows = strengthCheckins(gym, logs, sets);
    const bench = rows.find((r) => r.exerciseId === "bench_press")!;
    expect(gym.tracking.strengthCheckins!.weeks).toEqual([1, 4, 8, 12]);
    expect(bench.byWeek.map((s) => (s ? `${s.weightKg}×${s.reps}` : null))).toEqual([
      "60×10",
      "62.5×10", // e1RM 83.3 beats 65×8 (82.3)
      null,
      null,
    ]);
  });
});
