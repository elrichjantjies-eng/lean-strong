import { createContext, useContext } from "react";
import type { Exercise, Program } from "@/content/types";
import type { Profile } from "@/domain/model";
import type { ResolvedItem, ResolvedSession } from "@/domain/session";
import type { WorkoutSnapshot } from "./store";
import type { Step } from "./steps";

export interface PlayerCtx {
  program: Program;
  profile: Profile;
  resolved: ResolvedSession;
  steps: Step[];
  index: number;
  snap: WorkoutSnapshot;
}

export const PlayerContext = createContext<PlayerCtx | null>(null);

export function usePlayerCtx(): PlayerCtx {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error("usePlayerCtx outside the player");
  return ctx;
}

/** The pair item and the exercise actually being done (after any swap). */
export function itemAt(
  ctx: Pick<PlayerCtx, "program" | "resolved" | "snap">,
  pairIndex: number,
  itemIndex: 0 | 1,
): { item: ResolvedItem; exercise: Exercise } {
  const item = ctx.resolved.pairs[pairIndex]!.items[itemIndex];
  const id = ctx.snap.mods[item.code]?.exerciseId ?? item.exerciseId;
  return { item, exercise: ctx.program.exercises[id]! };
}

/** "8–10 reps", "12 reps each side", "30–45 s". */
export function targetText(t: {
  kind: "reps" | "time";
  min: number;
  max: number;
  perSide: boolean;
}) {
  const range = t.min === t.max ? `${t.max}` : `${t.min}–${t.max}`;
  const unit = t.kind === "time" ? " s" : " reps";
  return `${range}${unit}${t.perSide ? " each side" : ""}`;
}

/** Section label for the top bar. */
export function stepSection(step: Step, ctx: Pick<PlayerCtx, "resolved" | "program">): string {
  switch (step.type) {
    case "overview":
      return "Overview";
    case "rope":
    case "mobility":
    case "warmupSets":
      return "Warm-up";
    case "set":
    case "switch":
    case "rest":
      return `Power sets · Pair ${ctx.resolved.pairs[step.pairIndex]!.code}`;
    case "finisher":
      return `Finisher · ${ctx.resolved.finisher?.name ?? ""}`;
    case "waist":
      return ctx.resolved.waistCircuit?.title ?? "Circuit";
    case "walk":
      return "Walk";
    case "cooldown":
      return "Cool-down";
    case "summary":
      return "Done";
  }
}
