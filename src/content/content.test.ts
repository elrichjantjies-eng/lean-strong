import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import gym from "./programs/gym-12wk.json";
import home from "./programs/home-9wk.json";
import { programSchema } from "./schema";
import { imagePaths, referenceProblems } from "./references";
import { programList } from "./loader";
import { fillPlaceholders, parseBold } from "./text";

const files = { "gym-12wk": gym, "home-9wk": home };

describe.each(Object.entries(files))("program %s", (id, raw) => {
  const result = programSchema.safeParse(raw);

  it("parses against the zod schema", () => {
    expect(result.error?.issues ?? []).toEqual([]);
    expect(result.data?.id).toBe(id);
  });

  it("references only exercises and sessions that exist", () => {
    expect(referenceProblems(result.data!)).toEqual([]);
  });

  it("has every image on disk", () => {
    const missing = imagePaths(result.data!).filter(
      (p) => !existsSync(resolve(import.meta.dirname, "../../public", p)),
    );
    expect(missing).toEqual([]);
  });

  it("has phases covering every week exactly once", () => {
    const program = result.data!;
    for (let w = 1; w <= program.weeks; w++) {
      expect(program.phases.filter((p) => p.weeks[0] <= w && w <= p.weeks[1])).toHaveLength(1);
    }
  });

  it("has a weekTemplate that starts on Monday", () => {
    expect(result.data!.weekTemplate.map((d) => d.weekday)).toEqual([
      "mon",
      "tue",
      "wed",
      "thu",
      "fri",
      "sat",
      "sun",
    ]);
  });

  it("has a step target for every week", () => {
    expect(result.data!.tracking.steps.dailyTargetByWeek).toHaveLength(result.data!.weeks);
  });
});

describe("loader", () => {
  it("loads both programs", () => {
    expect(programList.map((p) => p.id).sort()).toEqual(["gym-12wk", "home-9wk"]);
  });
});

describe("text helpers", () => {
  it("fills known placeholders and leaves unknown ones", () => {
    expect(
      fillPlaceholders("From {startKg} to {goalKg} kg in {weeks} weeks {x}", {
        startKg: 80,
        goalKg: 75,
        weeks: 12,
      }),
    ).toBe("From 80 to 75 kg in 12 weeks {x}");
  });

  it("splits bold runs", () => {
    expect(parseBold("Go up **+2.5 kg** today")).toEqual([
      { text: "Go up ", bold: false },
      { text: "+2.5 kg", bold: true },
      { text: " today", bold: false },
    ]);
  });
});
