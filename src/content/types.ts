/**
 * Types for content/programs/*.json — the single source of truth for program content.
 *
 * Text conventions used in every string field:
 *   **bold**        → render as bold (that's the only inline markup)
 *   {placeholder}   → fill from the user's profile at render time:
 *       {startKg} {goalKg} {weeks} {ratePerWeek} {weeksToGoal} {checkpointWeek} {checkpointKg}
 *   “–” is an en dash used for ranges (8–10, weeks 1–4); keep it.
 */

export type Weekday = "mon" | "tue" | "wed" | "thu" | "fri" | "sat" | "sun";
export type PhaseTriple<T> = [T, T, T]; // index 0 = phase 1, 1 = phase 2, 2 = phase 3
export type MinMax = [number, number];

export interface Program {
  schemaVersion: 1;
  id: "gym-12wk" | "home-9wk" | string;
  title: string;
  subtitle: string;
  summary: string;
  setting: "gym" | "home";
  weeks: number;
  theme: Theme;
  equipmentNeeded: string[];
  weightPlan: WeightPlan;
  phases: Phase[];
  /** Exactly 7 entries, Monday first. */
  weekTemplate: DayPlan[];
  sessions: Record<string, Session>;
  exercises: Record<string, Exercise>;
  guideSections: { title: string; exerciseIds: string[] }[];
  pages: Page[];
  tracking: Tracking;
  coaching: Coaching;
  /** Home program only: walk-day interval structure. */
  walkingIntervals?: {
    fromWeek: number;
    warmupMin: number;
    cooldownMin: number;
    roundsRange: MinMax;
    briskSec: number;
    fastSec: number;
  };
}

export interface Theme {
  primary: string;
  ink: string;
  muted: string;
  line: string;
  /** Background for exercise illustration panels — keep it light even in dark mode. */
  panel: string;
  accent: string;
  accentSoft: string;
  phaseColors: PhaseTriple<string>;
  /** Keys: session ids (A, B, C, H) plus "walk" and "rest". */
  dayColors: Record<string, string>;
}

export interface WeightPlan {
  /** Pace the target line uses by default (kg lost per week). */
  defaultRateKgPerWeek: number;
  /** Never plan faster than this, whatever goal the user enters. */
  maxRateKgPerWeek: number;
  /** Weekly loss range shown as the shaded "on track" zone on the weight chart. */
  onTrackBandKgPerWeek: MinMax;
  checkpointWeek: number | null;
  calorieFloorKcal: number | null;
  proteinGPerDay: MinMax;
}

export interface Interval {
  activity: string;
  rounds: number;
  workSec: number;
  restSec: number;
  label?: string;
}

export interface Phase {
  number: 1 | 2 | 3;
  name: string;
  weeks: MinMax; // inclusive
  /** Human-readable rows for the phase table. */
  summary: {
    sets: string;
    effort: string;
    rest: string;
    warmup: string;
    waistCircuit?: string;
    walkAfter?: string;
  };
  restSeconds: number;
  ropeWarmup: Interval;
}

export interface DayPlan {
  weekday: Weekday;
  kind: "workout" | "walk" | "rest";
  title: string;
  subtitle: string;
  sessionId?: string;
  walk?: {
    minutesByPhase: PhaseTriple<MinMax>;
    intervalsFromWeek?: number | null;
    optional?: boolean;
  };
}

export interface Target {
  kind: "reps" | "time"; // time = seconds
  min: number;
  max: number;
  /** true = the number is per leg / arm / side */
  perSide: boolean;
  note?: string;
  /** Overrides min/max per phase when present. */
  byPhase?: PhaseTriple<{ min: number; max: number }>;
  /** Extra coaching note per phase (null = none). */
  noteByPhase?: PhaseTriple<string | null>;
}

export interface PairItem {
  code: string; // "A1"
  exerciseId: string;
  setsByPhase: PhaseTriple<number>;
  /** Exactly what the PDF printed in the reps column. */
  display: string;
  target: Target;
}

export interface Pair {
  code: string; // "A"
  items: [PairItem, PairItem];
}

export interface CircuitStep {
  exerciseId: string;
  display: string;
  target: Target;
}

export interface Circuit {
  title: string;
  description?: string;
  roundsByPhase: PhaseTriple<number>;
  restBetweenRoundsSec: number;
  /** Add `addSec` to every timed hold from `fromWeek` on. */
  holdBonus?: { fromWeek: number; addSec: number };
  steps: CircuitStep[];
}

export type Finisher =
  | { name: string; description: string; kind: "intervals"; byPhase: PhaseTriple<Interval>; alternative?: string }
  | { name: string; description: string; kind: "circuit"; circuit: Circuit }
  | {
      name: string;
      description: string;
      kind: "choice";
      options: ({ label: string; interval: Interval } | { label: string; durationMin: MinMax })[];
    };

export interface Session {
  id: string;
  title: string;
  focus: string;
  weekdayLabel: string;
  color: string;
  intro: string;
  warmup: {
    text: string;
    /** "phase" = use the current phase's ropeWarmup. */
    rope: "phase" | Interval;
    alternative?: string;
    mobility: string[];
    warmupSets?: string;
  };
  pairs: Pair[];
  restSecondsByPhase: PhaseTriple<number>;
  restNote: string;
  finisher?: Finisher;
  waistCircuit?: Circuit;
  walkAfter?: { minutesByPhase: PhaseTriple<MinMax>; text: string };
  notes: string[];
  equipment?: string[];
  cooldown: { text: string; stretches: string[]; holdSec: number };
}

export type Progression =
  | { type: "load"; incrementKg: number; note?: string }
  | { type: "reps-then-harder"; options: string[] }
  | { type: "time"; addSecMin: number; addSecMax: number }
  | { type: "none" };

export interface Exercise {
  id: string;
  name: string;
  muscles: string[];
  cues: string[];
  watchOut: string;
  images: {
    start: string; // picture 1 (start position)
    end: string; // picture 2 (end position, with faint start ghost + arrow)
    loop: string | null; // looping animated demo, null when the two pictures are variants
  };
  frameLabels: [string, string];
  /** true = picture 2 is an easier variant (e.g. plank on knees), not the end of a movement. */
  framesAreVariants: boolean;
  load: "barbell" | "dumbbell" | "kettlebell" | "machine" | "cable" | "bodyweight" | "ankle-weights" | "cardio";
  logAs: "weight-reps" | "reps" | "time" | "none";
  equipment: string[];
  progression: Progression;
}

export type Section =
  | { type: "text"; title?: string; body: string }
  | { type: "weekStrip"; title?: string; note?: string } // render from weekTemplate
  | { type: "powerSets"; boxes: [string, string, string][]; note?: string }
  | { type: "sessionFlow"; title?: string; steps: [string, string][]; highlight: number[] } // highlight = 1-based
  | { type: "callout"; tone: "info" | "accent"; title?: string; items: string[] }
  | { type: "phaseTable"; rows: (keyof Phase["summary"])[]; rowLabels: string[]; note?: string }
  | { type: "steps"; title?: string; items: string[] }
  | { type: "cards"; cards: { title: string; color: string; items: string[] }[] } // color: hex or theme key
  | { type: "table"; columns: string[]; rows: string[][]; note?: string }
  | { type: "sampleDay"; title: string; intro: string; rows: [string, string][] }
  | { type: "disclaimer"; body: string };

export interface Page {
  id: string;
  title: string;
  kicker: string;
  sections: Section[];
}

export interface MeasurementField {
  id: string;
  label: string;
  unit: string;
  hint: string;
}

export interface FitnessTest {
  id: string;
  label: string;
  unit: string;
  method: "stopwatch" | "count" | "timed-count" | "entry";
  durationSec?: number;
  exerciseId?: string;
}

export interface Tracking {
  weighIn: { text: string; suggestedDays: Weekday[] };
  measurements: { everyWeeks: number; fields: MeasurementField[] };
  strengthCheckins: { weeks: number[]; exerciseIds: string[]; text: string } | null;
  fitnessTests: { weeks: number[]; text: string; tests: FitnessTest[] } | null;
  steps: { dailyTargetByWeek: number[]; text: string };
  wins: string[];
  daily: { calories: boolean; protein: boolean; water: boolean };
  sitBreaks?: { everyMinutes: MinMax; moveMinutes: MinMax; text: string };
}

export interface CoachingBand {
  minKgPerWeek?: number; // inclusive
  maxKgPerWeek?: number; // exclusive
  status: "stalled" | "slow" | "on-track" | "fast";
  message: string;
}

export interface Coaching {
  /** How often the check-in compares weekly averages. */
  reviewEveryWeeks: number;
  /** Need at least this many weekly averages before giving advice. */
  minWeeksOfData: number;
  bands: CoachingBand[];
  notes: string[];
}
