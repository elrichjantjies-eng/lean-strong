import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import { sessionLog, setLog, sets } from "@/test/factories";
import {
  bestSet,
  computeBests,
  epley,
  lastPerformance,
  newBests,
  prefillSet,
  shouldShowDumbbellTip,
  suggestProgression,
  suggestionText,
} from "./progression";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");
const ex = (id: string, program = gym) => program.exercises[id]!;

describe("Epley", () => {
  it("is weight × (1 + reps / 30)", () => {
    expect(epley(100, 10)).toBeCloseTo(133.333, 3);
    expect(epley(60, 1)).toBeCloseTo(62, 10);
    expect(epley(80, 0)).toBe(80);
  });
});

describe("last performance and prefill", () => {
  const older = sessionLog({ id: "old", finishedAt: 1_000 });
  const newer = sessionLog({ id: "new", sessionId: "C", finishedAt: 5_000 });
  const running = sessionLog({
    id: "run",
    status: "in-progress",
    startedAt: 9_000,
    finishedAt: undefined,
  });
  const all = [
    ...sets("old", [
      [50, 10],
      [50, 10],
      [50, 9],
    ]),
    ...sets("new", [
      [60, 10],
      [60, 9],
    ]),
    ...sets("run", [[70, 5]]),
    setLog({ sessionLogId: "new", setIndex: 5, weightKg: 20, reps: 10, isWarmup: true }),
  ];

  it("uses the most recent completed log, in any session, without warm-ups", () => {
    const last = lastPerformance("bench_press", [older, newer, running], all);
    expect(last?.log.id).toBe("new");
    expect(last?.sets.map((s) => [s.weightKg, s.reps])).toEqual([
      [60, 10],
      [60, 9],
    ]);
  });

  it("prefills from the matching set, else the last set, else nothing", () => {
    const last = lastPerformance("bench_press", [older, newer], all);
    expect(prefillSet(last, 1)).toEqual({ weightKg: 60, reps: 9 });
    expect(prefillSet(last, 3)).toEqual({ weightKg: 60, reps: 9 });
    expect(prefillSet(null, 0)).toEqual({});
  });

  it("ignores finisher and circuit sets", () => {
    const circuit = [
      setLog({ sessionLogId: "old", exerciseId: "plank", blockType: "circuit", seconds: 40 }),
    ];
    expect(lastPerformance("plank", [older], circuit)).toBeNull();
  });
});

describe("progression suggestions", () => {
  const top = sets("x", [
    [60, 10],
    [60, 10],
    [60, 10],
  ]);

  it("load: every planned set at the top at the same weight → go up", () => {
    const s = suggestProgression(ex("bench_press"), "gym", { sets: top, plannedSets: 3, max: 10 });
    expect(s).toEqual({ kind: "go-up", incrementKg: 2.5 });
    expect(suggestionText(s!)).toBe("Go up **+2.5 kg** today");
  });

  it("load: includes the note", () => {
    const s = suggestProgression(ex("incline_db_press"), "gym", {
      sets: sets("x", [
        [20, 12],
        [20, 12],
        [20, 12],
      ]),
      plannedSets: 3,
      max: 12,
    });
    expect(suggestionText(s!)).toBe("Go up **+2 kg** today · per dumbbell (1–2 kg)");
  });

  it("load: otherwise → same weight, one more rep", () => {
    const missed = sets("x", [
      [60, 10],
      [60, 10],
      [60, 9],
    ]);
    const mixed = sets("x", [
      [60, 10],
      [62.5, 10],
      [60, 10],
    ]);
    const short = sets("x", [
      [60, 10],
      [60, 10],
    ]);
    for (const s of [missed, mixed]) {
      expect(
        suggestProgression(ex("bench_press"), "gym", { sets: s, plannedSets: 3, max: 10 }),
      ).toEqual({
        kind: "same-weight",
      });
    }
    // Fewer sets than planned (e.g. 4 planned in phase 2).
    expect(
      suggestProgression(ex("bench_press"), "gym", { sets: short, plannedSets: 3, max: 10 })?.kind,
    ).toBe("same-weight");
    expect(suggestionText({ kind: "same-weight" })).toBe(
      "Same weight. Aim for one more rep than last time.",
    );
  });

  it("reps-then-harder: top of the range → options", () => {
    const s = suggestProgression(ex("push_up"), "gym", {
      sets: sets(
        "x",
        [
          [undefined, 15],
          [undefined, 15],
          [undefined, 15],
        ],
        "push_up",
      ),
      plannedSets: 3,
      max: 15,
    });
    expect(s).toEqual({
      kind: "make-harder",
      options: ["Add reps", "Lower for a slow count of 3", "Feet up on a step"],
    });
    expect(suggestionText(s!)).toBe("You've hit the top of the range. Make it harder:");
    const not = suggestProgression(ex("push_up"), "gym", {
      sets: sets(
        "x",
        [
          [undefined, 15],
          [undefined, 12],
        ],
        "push_up",
      ),
      plannedSets: 2,
      max: 15,
    });
    expect(not?.kind).toBe("one-more-rep");
  });

  it("time: every hold at the top → add seconds", () => {
    const holds = [0, 1, 2].map((i) => setLog({ exerciseId: "plank", setIndex: i, seconds: 60 }));
    const s = suggestProgression(ex("plank"), "gym", { sets: holds, plannedSets: 3, max: 60 });
    expect(suggestionText(s!)).toBe("Add 5–10 s");
    const climber = suggestProgression(ex("mountain_climber"), "gym", {
      sets: holds,
      plannedSets: 3,
      max: 30,
    });
    expect(suggestionText(climber!)).toBe("Add 10 s");
    const short = suggestProgression(ex("plank"), "gym", { sets: holds, plannedSets: 3, max: 61 });
    expect(short?.kind).toBe("hold-longer");
  });

  it("no history → week-1 guidance per program", () => {
    expect(suggestProgression(ex("bench_press"), "gym", null)).toEqual({
      kind: "first-time",
      text: "Use about 60% of what you think you can lift",
    });
    expect(suggestProgression(ex("kb_goblet_squat", home), "home", null)).toEqual({
      kind: "first-time",
      text: "Go easy and learn the movement",
    });
    expect(suggestProgression(ex("skipping"), "gym", null)).toBeNull();
  });

  it("home-9wk: suggests 8 kg dumbbells once, on the listed moves at 5 kg", () => {
    const at5 = {
      sets: sets(
        "x",
        [
          [5, 15],
          [5, 15],
          [5, 15],
        ],
        "db_bent_over_row",
      ),
      plannedSets: 3,
      max: 15,
    };
    expect(shouldShowDumbbellTip("home-9wk", "db_bent_over_row", at5, false)).toBe(true);
    expect(shouldShowDumbbellTip("home-9wk", "db_bent_over_row", at5, true)).toBe(false);
    expect(shouldShowDumbbellTip("home-9wk", "hammer_curl", at5, false)).toBe(false);
    expect(shouldShowDumbbellTip("gym-12wk", "db_bent_over_row", at5, false)).toBe(false);
    const at8 = {
      ...at5,
      sets: sets(
        "x",
        [
          [8, 15],
          [8, 15],
          [8, 15],
        ],
        "db_bent_over_row",
      ),
    };
    expect(shouldShowDumbbellTip("home-9wk", "db_bent_over_row", at8, false)).toBe(false);
  });
});

describe("personal bests", () => {
  it("tracks best e1RM, heaviest weight and most reps per weight", () => {
    const b = computeBests(
      sets("x", [
        [60, 10],
        [70, 5],
        [60, 12],
      ]),
    );
    expect(b.e1rm).toBeCloseTo(84, 5); // 60 × (1 + 12/30)
    expect(b.heaviest).toBe(70);
    expect(b.repsAtWeight).toEqual({ "60": 12, "70": 5 });
  });

  it("reports new bests against history, not on the first log", () => {
    const before = sets("a", [
      [60, 10],
      [60, 10],
    ]);
    const session = sets("b", [
      [62.5, 10],
      [60, 11],
    ]);
    expect(newBests(before, session)).toEqual([
      { kind: "e1rm", value: epley(62.5, 10) },
      { kind: "heaviest", value: 62.5 },
      { kind: "reps", weightKg: 60, reps: 11 },
    ]);
    expect(newBests([], session)).toEqual([]);
    expect(newBests(before, sets("c", [[60, 10]]))).toEqual([]);
  });

  it("picks the best set by estimated 1RM", () => {
    expect(
      bestSet(
        sets("x", [
          [60, 10],
          [70, 5],
          [65, 8],
        ]),
      )?.weightKg,
    ).toBe(65);
  });
});
