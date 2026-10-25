/** 5.2 What a session contains in week `w`. */
import type {
  Circuit,
  DayPlan,
  Finisher,
  Interval,
  MinMax,
  PairItem,
  Program,
  Session,
  Target,
} from "@/content/types";
import { phaseForWeek } from "./schedule";

export interface ResolvedTarget {
  kind: Target["kind"];
  min: number;
  max: number;
  perSide: boolean;
  note?: string;
}

export interface ResolvedItem {
  code: string;
  exerciseId: string;
  sets: number;
  display: string;
  target: ResolvedTarget;
}

export interface ResolvedCircuit {
  title: string;
  description?: string;
  rounds: number;
  restSec: number;
  steps: { exerciseId: string; display: string; target: ResolvedTarget }[];
}

export type ResolvedFinisher =
  | {
      kind: "intervals";
      name: string;
      description: string;
      interval: Interval;
      alternative?: string;
    }
  | { kind: "circuit"; name: string; description: string; circuit: ResolvedCircuit }
  | {
      kind: "choice";
      name: string;
      description: string;
      options: Extract<Finisher, { kind: "choice" }>["options"];
    };

export interface ResolvedSession {
  session: Session;
  week: number;
  p: number;
  rope: Interval;
  restSec: number;
  pairs: { code: string; items: [ResolvedItem, ResolvedItem] }[];
  finisher?: ResolvedFinisher;
  waistCircuit?: ResolvedCircuit;
  walkAfterMin?: MinMax;
  estimatedMinutes: number;
}

/** Target for phase index `p`: `byPhase[p] ?? {min,max}`, note `noteByPhase[p] ?? note`. */
export function resolveTarget(target: Target, p: number): ResolvedTarget {
  const range = target.byPhase?.[p] ?? { min: target.min, max: target.max };
  const note = target.noteByPhase?.[p] ?? target.note;
  return {
    kind: target.kind,
    min: range.min,
    max: range.max,
    perSide: target.perSide,
    ...(note ? { note } : {}),
  };
}

export function resolveItem(item: PairItem, p: number): ResolvedItem {
  return {
    code: item.code,
    exerciseId: item.exerciseId,
    sets: item.setsByPhase[p]!,
    display: item.display,
    target: resolveTarget(item.target, p),
  };
}

/** Rounds for the phase; timed holds get `holdBonus.addSec` from `holdBonus.fromWeek`. */
export function resolveCircuit(circuit: Circuit, p: number, week: number): ResolvedCircuit {
  const bonus =
    circuit.holdBonus && week >= circuit.holdBonus.fromWeek ? circuit.holdBonus.addSec : 0;
  return {
    title: circuit.title,
    ...(circuit.description ? { description: circuit.description } : {}),
    rounds: circuit.roundsByPhase[p]!,
    restSec: circuit.restBetweenRoundsSec,
    steps: circuit.steps.map((step) => {
      const target = resolveTarget(step.target, p);
      if (target.kind === "time" && bonus) {
        target.min += bonus;
        target.max += bonus;
      }
      return { exerciseId: step.exerciseId, display: step.display, target };
    }),
  };
}

export function resolveFinisher(finisher: Finisher, p: number, week: number): ResolvedFinisher {
  switch (finisher.kind) {
    case "intervals":
      return {
        kind: "intervals",
        name: finisher.name,
        description: finisher.description,
        interval: finisher.byPhase[p]!,
        ...(finisher.alternative ? { alternative: finisher.alternative } : {}),
      };
    case "circuit":
      return {
        kind: "circuit",
        name: finisher.name,
        description: finisher.description,
        circuit: resolveCircuit(finisher.circuit, p, week),
      };
    case "choice":
      return {
        kind: "choice",
        name: finisher.name,
        description: finisher.description,
        options: finisher.options,
      };
  }
}

const intervalSec = (i: Interval) => i.rounds * (i.workSec + i.restSec);
const avg = ([a, b]: MinMax) => (a + b) / 2;
/** Rough time for one set or hold: ~3 s per rep, or the hold itself, plus setup. */
const workSec = (t: ResolvedTarget) =>
  (t.kind === "time" ? avg([t.min, t.max]) : avg([t.min, t.max]) * 3) * (t.perSide ? 2 : 1) + 10;

function circuitSec(c: ResolvedCircuit): number {
  const round = c.steps.reduce((sum, s) => sum + workSec(s.target), 0);
  return c.rounds * round + (c.rounds - 1) * c.restSec;
}

export function resolveSession(program: Program, sessionId: string, week: number): ResolvedSession {
  const session = program.sessions[sessionId];
  if (!session) throw new Error(`Unknown session "${sessionId}"`);
  const phase = phaseForWeek(program, week);
  const p = phase.number - 1;
  const rope = session.warmup.rope === "phase" ? phase.ropeWarmup : session.warmup.rope;
  const restSec = session.restSecondsByPhase[p]!;
  const pairs = session.pairs.map((pair) => ({
    code: pair.code,
    items: [resolveItem(pair.items[0], p), resolveItem(pair.items[1], p)] as [
      ResolvedItem,
      ResolvedItem,
    ],
  }));
  const finisher = session.finisher ? resolveFinisher(session.finisher, p, week) : undefined;
  const waistCircuit = session.waistCircuit
    ? resolveCircuit(session.waistCircuit, p, week)
    : undefined;
  const walkAfterMin = session.walkAfter?.minutesByPhase[p];

  // Estimated duration from the plan's own numbers.
  let sec = intervalSec(rope) + session.warmup.mobility.length * 30;
  for (const pair of pairs) {
    const sets = Math.max(pair.items[0].sets, pair.items[1].sets);
    sec += sets * (workSec(pair.items[0].target) + 15 + workSec(pair.items[1].target) + restSec);
  }
  if (finisher?.kind === "intervals") sec += intervalSec(finisher.interval);
  if (finisher?.kind === "circuit") sec += circuitSec(finisher.circuit);
  if (finisher?.kind === "choice") {
    const first = finisher.options[0];
    if (first)
      sec += "interval" in first ? intervalSec(first.interval) : avg(first.durationMin) * 60;
  }
  if (waistCircuit) sec += circuitSec(waistCircuit);
  if (walkAfterMin) sec += avg(walkAfterMin) * 60;
  sec += session.cooldown.stretches.length * session.cooldown.holdSec;

  return {
    session,
    week,
    p,
    rope,
    restSec,
    pairs,
    finisher,
    waistCircuit,
    walkAfterMin,
    estimatedMinutes: Math.round(sec / 60 / 5) * 5,
  };
}

export interface WalkPlan {
  minutes: MinMax;
  optional: boolean;
  intervals?: NonNullable<Program["walkingIntervals"]>;
}

/** Walk-day minutes for the phase, plus walking intervals from `intervalsFromWeek` on. */
export function walkForDay(program: Program, day: DayPlan, week: number): WalkPlan | null {
  if (!day.walk) return null;
  const p = phaseForWeek(program, week).number - 1;
  const from = day.walk.intervalsFromWeek;
  const intervals =
    from != null && week >= from && program.walkingIntervals ? program.walkingIntervals : undefined;
  return {
    minutes: day.walk.minutesByPhase[p]!,
    optional: !!day.walk.optional,
    ...(intervals ? { intervals } : {}),
  };
}
