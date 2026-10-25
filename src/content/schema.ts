/**
 * Runtime schema mirroring `types.ts`. The compile-time checks at the bottom make sure the two
 * never drift apart: the parsed value must be assignable to `Program`, and vice versa.
 */
import { z } from "zod";
import type { Program } from "./types";

const triple = <T extends z.ZodType>(t: T) => z.tuple([t, t, t]);
const minMax = z.tuple([z.number(), z.number()]);
const weekday = z.enum(["mon", "tue", "wed", "thu", "fri", "sat", "sun"]);

const interval = z.strictObject({
  activity: z.string(),
  rounds: z.number().int().positive(),
  workSec: z.number().positive(),
  restSec: z.number().nonnegative(),
  label: z.string().optional(),
});

const theme = z.strictObject({
  primary: z.string(),
  ink: z.string(),
  muted: z.string(),
  line: z.string(),
  panel: z.string(),
  accent: z.string(),
  accentSoft: z.string(),
  phaseColors: triple(z.string()),
  dayColors: z.record(z.string(), z.string()),
});

const weightPlan = z.strictObject({
  defaultRateKgPerWeek: z.number(),
  maxRateKgPerWeek: z.number(),
  onTrackBandKgPerWeek: minMax,
  checkpointWeek: z.number().int().nullable(),
  calorieFloorKcal: z.number().nullable(),
  proteinGPerDay: minMax,
});

const phase = z.strictObject({
  number: z.union([z.literal(1), z.literal(2), z.literal(3)]),
  name: z.string(),
  weeks: minMax,
  summary: z.strictObject({
    sets: z.string(),
    effort: z.string(),
    rest: z.string(),
    warmup: z.string(),
    waistCircuit: z.string().optional(),
    walkAfter: z.string().optional(),
  }),
  restSeconds: z.number(),
  ropeWarmup: interval,
});

const dayPlan = z.strictObject({
  weekday,
  kind: z.enum(["workout", "walk", "rest"]),
  title: z.string(),
  subtitle: z.string(),
  sessionId: z.string().optional(),
  walk: z
    .strictObject({
      minutesByPhase: triple(minMax),
      intervalsFromWeek: z.number().int().nullable().optional(),
      optional: z.boolean().optional(),
    })
    .optional(),
});

const minMaxObj = z.strictObject({ min: z.number(), max: z.number() });

const target = z.strictObject({
  kind: z.enum(["reps", "time"]),
  min: z.number(),
  max: z.number(),
  perSide: z.boolean(),
  note: z.string().optional(),
  byPhase: triple(minMaxObj).optional(),
  noteByPhase: triple(z.string().nullable()).optional(),
});

const pairItem = z.strictObject({
  code: z.string(),
  exerciseId: z.string(),
  setsByPhase: triple(z.number().int().nonnegative()),
  display: z.string(),
  target,
});

const pair = z.strictObject({ code: z.string(), items: z.tuple([pairItem, pairItem]) });

const circuitStep = z.strictObject({ exerciseId: z.string(), display: z.string(), target });

const circuit = z.strictObject({
  title: z.string(),
  description: z.string().optional(),
  roundsByPhase: triple(z.number().int().positive()),
  restBetweenRoundsSec: z.number(),
  holdBonus: z.strictObject({ fromWeek: z.number().int(), addSec: z.number() }).optional(),
  steps: z.array(circuitStep),
});

const finisher = z.discriminatedUnion("kind", [
  z.strictObject({
    name: z.string(),
    description: z.string(),
    kind: z.literal("intervals"),
    byPhase: triple(interval),
    alternative: z.string().optional(),
  }),
  z.strictObject({
    name: z.string(),
    description: z.string(),
    kind: z.literal("circuit"),
    circuit,
  }),
  z.strictObject({
    name: z.string(),
    description: z.string(),
    kind: z.literal("choice"),
    options: z.array(
      z.union([
        z.strictObject({ label: z.string(), interval }),
        z.strictObject({ label: z.string(), durationMin: minMax }),
      ]),
    ),
  }),
]);

const session = z.strictObject({
  id: z.string(),
  title: z.string(),
  focus: z.string(),
  weekdayLabel: z.string(),
  color: z.string(),
  intro: z.string(),
  warmup: z.strictObject({
    text: z.string(),
    rope: z.union([z.literal("phase"), interval]),
    alternative: z.string().optional(),
    mobility: z.array(z.string()),
    warmupSets: z.string().optional(),
  }),
  pairs: z.array(pair),
  restSecondsByPhase: triple(z.number()),
  restNote: z.string(),
  finisher: finisher.optional(),
  waistCircuit: circuit.optional(),
  walkAfter: z.strictObject({ minutesByPhase: triple(minMax), text: z.string() }).optional(),
  notes: z.array(z.string()),
  equipment: z.array(z.string()).optional(),
  cooldown: z.strictObject({
    text: z.string(),
    stretches: z.array(z.string()),
    holdSec: z.number(),
  }),
});

const progression = z.discriminatedUnion("type", [
  z.strictObject({ type: z.literal("load"), incrementKg: z.number(), note: z.string().optional() }),
  z.strictObject({ type: z.literal("reps-then-harder"), options: z.array(z.string()) }),
  z.strictObject({ type: z.literal("time"), addSecMin: z.number(), addSecMax: z.number() }),
  z.strictObject({ type: z.literal("none") }),
]);

const exercise = z.strictObject({
  id: z.string(),
  name: z.string(),
  muscles: z.array(z.string()),
  cues: z.array(z.string()),
  watchOut: z.string(),
  images: z.strictObject({ start: z.string(), end: z.string(), loop: z.string().nullable() }),
  frameLabels: z.tuple([z.string(), z.string()]),
  framesAreVariants: z.boolean(),
  load: z.enum([
    "barbell",
    "dumbbell",
    "kettlebell",
    "machine",
    "cable",
    "bodyweight",
    "ankle-weights",
    "cardio",
  ]),
  logAs: z.enum(["weight-reps", "reps", "time", "none"]),
  equipment: z.array(z.string()),
  progression,
});

const phaseSummaryKey = z.enum(["sets", "effort", "rest", "warmup", "waistCircuit", "walkAfter"]);

const section = z.discriminatedUnion("type", [
  z.strictObject({ type: z.literal("text"), title: z.string().optional(), body: z.string() }),
  z.strictObject({
    type: z.literal("weekStrip"),
    title: z.string().optional(),
    note: z.string().optional(),
  }),
  z.strictObject({
    type: z.literal("powerSets"),
    boxes: z.array(z.tuple([z.string(), z.string(), z.string()])),
    note: z.string().optional(),
  }),
  z.strictObject({
    type: z.literal("sessionFlow"),
    title: z.string().optional(),
    steps: z.array(z.tuple([z.string(), z.string()])),
    highlight: z.array(z.number().int()),
  }),
  z.strictObject({
    type: z.literal("callout"),
    tone: z.enum(["info", "accent"]),
    title: z.string().optional(),
    items: z.array(z.string()),
  }),
  z.strictObject({
    type: z.literal("phaseTable"),
    rows: z.array(phaseSummaryKey),
    rowLabels: z.array(z.string()),
    note: z.string().optional(),
  }),
  z.strictObject({
    type: z.literal("steps"),
    title: z.string().optional(),
    items: z.array(z.string()),
  }),
  z.strictObject({
    type: z.literal("cards"),
    cards: z.array(
      z.strictObject({ title: z.string(), color: z.string(), items: z.array(z.string()) }),
    ),
  }),
  z.strictObject({
    type: z.literal("table"),
    columns: z.array(z.string()),
    rows: z.array(z.array(z.string())),
    note: z.string().optional(),
  }),
  z.strictObject({
    type: z.literal("sampleDay"),
    title: z.string(),
    intro: z.string(),
    rows: z.array(z.tuple([z.string(), z.string()])),
  }),
  z.strictObject({ type: z.literal("disclaimer"), body: z.string() }),
]);

const page = z.strictObject({
  id: z.string(),
  title: z.string(),
  kicker: z.string(),
  sections: z.array(section),
});

const tracking = z.strictObject({
  weighIn: z.strictObject({ text: z.string(), suggestedDays: z.array(weekday) }),
  measurements: z.strictObject({
    everyWeeks: z.number().int().positive(),
    fields: z.array(
      z.strictObject({ id: z.string(), label: z.string(), unit: z.string(), hint: z.string() }),
    ),
  }),
  strengthCheckins: z
    .strictObject({
      weeks: z.array(z.number().int()),
      exerciseIds: z.array(z.string()),
      text: z.string(),
    })
    .nullable(),
  fitnessTests: z
    .strictObject({
      weeks: z.array(z.number().int()),
      text: z.string(),
      tests: z.array(
        z.strictObject({
          id: z.string(),
          label: z.string(),
          unit: z.string(),
          method: z.enum(["stopwatch", "count", "timed-count", "entry"]),
          durationSec: z.number().optional(),
          exerciseId: z.string().optional(),
        }),
      ),
    })
    .nullable(),
  steps: z.strictObject({ dailyTargetByWeek: z.array(z.number()), text: z.string() }),
  wins: z.array(z.string()),
  daily: z.strictObject({ calories: z.boolean(), protein: z.boolean(), water: z.boolean() }),
  sitBreaks: z
    .strictObject({ everyMinutes: minMax, moveMinutes: minMax, text: z.string() })
    .optional(),
});

const coaching = z.strictObject({
  reviewEveryWeeks: z.number().int().positive(),
  minWeeksOfData: z.number().int().positive(),
  bands: z.array(
    z.strictObject({
      minKgPerWeek: z.number().optional(),
      maxKgPerWeek: z.number().optional(),
      status: z.enum(["stalled", "slow", "on-track", "fast"]),
      message: z.string(),
    }),
  ),
  notes: z.array(z.string()),
});

export const programSchema = z.strictObject({
  schemaVersion: z.literal(1),
  id: z.string(),
  title: z.string(),
  subtitle: z.string(),
  summary: z.string(),
  setting: z.enum(["gym", "home"]),
  weeks: z.number().int().positive(),
  theme,
  equipmentNeeded: z.array(z.string()),
  weightPlan,
  phases: z.array(phase),
  weekTemplate: z.array(dayPlan).length(7),
  sessions: z.record(z.string(), session),
  exercises: z.record(z.string(), exercise),
  guideSections: z.array(z.strictObject({ title: z.string(), exerciseIds: z.array(z.string()) })),
  pages: z.array(page),
  tracking,
  coaching,
  walkingIntervals: z
    .strictObject({
      fromWeek: z.number().int(),
      warmupMin: z.number(),
      cooldownMin: z.number(),
      roundsRange: minMax,
      briskSec: z.number(),
      fastSec: z.number(),
    })
    .optional(),
});

// Compile-time drift checks between the schema and types.ts.
type Parsed = z.infer<typeof programSchema>;
type Assert<T extends true> = T;
export type SchemaMatchesTypes = Assert<Parsed extends Program ? true : false>;
export type TypesMatchSchema = Assert<Program extends Parsed ? true : false>;
